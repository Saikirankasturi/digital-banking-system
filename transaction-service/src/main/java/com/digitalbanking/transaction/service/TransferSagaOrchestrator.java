package com.digitalbanking.transaction.service;

import com.digitalbanking.common.dto.*;
import com.digitalbanking.common.enums.FraudDecision;
import com.digitalbanking.common.enums.SagaStatus;
import com.digitalbanking.common.enums.TransactionStatus;
import com.digitalbanking.common.events.TransferCompletedEvent;
import com.digitalbanking.common.events.TransferFailedEvent;
import com.digitalbanking.common.events.TransferInitiatedEvent;
import com.digitalbanking.common.exception.BankingException;
import com.digitalbanking.common.exception.DuplicateTransactionException;
import com.digitalbanking.transaction.client.AccountClient;
import com.digitalbanking.transaction.client.FraudClient;
import com.digitalbanking.transaction.domain.SagaInstance;
import com.digitalbanking.transaction.domain.SagaStepLog;
import com.digitalbanking.transaction.domain.TransferTransaction;
import com.digitalbanking.transaction.repository.SagaInstanceRepository;
import com.digitalbanking.transaction.repository.SagaStepLogRepository;
import com.digitalbanking.transaction.repository.TransferTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSagaOrchestrator {

    private final TransferTransactionRepository transactionRepository;
    private final SagaInstanceRepository sagaRepository;
    private final SagaStepLogRepository stepLogRepository;
    private final IdempotencyService idempotencyService;
    private final AccountClient accountClient;
    private final FraudClient fraudClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_EVENTS_TOPIC = "transaction-events";

    /**
     * Executes the Distributed Money Transfer SAGA.
     */
    public TransferResponse executeTransferSaga(TransferRequest request) {
        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        // 1. Idempotency Check
        Optional<TransferTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent hit for key {}: returning existing transaction {}", idempotencyKey, existing.get().getId());
            return mapToResponse(existing.get(), "Idempotent request: returning existing transaction state");
        }

        String transactionId = UUID.randomUUID().toString();
        String sagaId = UUID.randomUUID().toString();

        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey, transactionId);
        if (!lockAcquired) {
            throw new DuplicateTransactionException(idempotencyKey);
        }

        // 2. Initialize Transaction and SAGA instances
        TransferTransaction tx = TransferTransaction.builder()
                .id(transactionId)
                .sagaId(sagaId)
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(request.getSourceAccountId())
                .targetAccountId(request.getTargetAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(TransactionStatus.PENDING)
                .deviceId(request.getDeviceId())
                .ipAddress(request.getIpAddress())
                .location(request.getLocation())
                .build();
        tx = transactionRepository.save(tx);

        SagaInstance saga = SagaInstance.builder()
                .sagaId(sagaId)
                .transactionId(transactionId)
                .status(SagaStatus.STARTED)
                .currentStep("INITIATED")
                .build();
        saga = sagaRepository.save(saga);

        publishEvent(TRANSACTION_EVENTS_TOPIC, transactionId, TransferInitiatedEvent.builder()
                .transactionId(transactionId)
                .sagaId(sagaId)
                .sourceAccountId(request.getSourceAccountId())
                .targetAccountId(request.getTargetAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .idempotencyKey(idempotencyKey)
                .deviceId(request.getDeviceId())
                .ipAddress(request.getIpAddress())
                .location(request.getLocation())
                .build());

        // 3. Step 1: Real-Time Millisecond Fraud Evaluation
        FraudEvaluationResult fraudResult = evaluateFraudStep(tx, saga);
        tx.setFraudScore(fraudResult.getRiskScore());

        if (fraudResult.getDecision() == FraudDecision.REJECTED) {
            tx.setStatus(TransactionStatus.FRAUD_REJECTED);
            tx.setFailureStep("FRAUD_EVALUATION");
            tx.setFailureReason("Rejected by Fraud Rule Engine: " + String.join(", ", fraudResult.getTriggeredRules()));
            tx.setCompletedAt(Instant.now());
            transactionRepository.save(tx);

            saga.setStatus(SagaStatus.FAILED);
            saga.setFailureStep("FRAUD_EVALUATION");
            saga.setFailureReason(tx.getFailureReason());
            sagaRepository.save(saga);

            emitTransferFailedEvent(tx, saga, false);
            return mapToResponse(tx, "Transfer blocked by real-time fraud detection engine");
        }

        if (fraudResult.getDecision() == FraudDecision.REVIEW_FLAGGED) {
            tx.setStatus(TransactionStatus.FRAUD_FLAGGED);
            tx.setFailureStep("FRAUD_CHALLENGE");
            tx.setFailureReason("Verification required with OTP due to anomaly flags: " + String.join(", ", fraudResult.getTriggeredRules()));
            transactionRepository.save(tx);

            saga.setCurrentStep("OTP_VERIFICATION_PENDING");
            sagaRepository.save(saga);

            return mapToResponse(tx, "Suspicious activity detected: 2FA/OTP verification required before transfer can proceed");
        }

        // Fraud check passed cleanly
        saga.setStatus(SagaStatus.FRAUD_CHECK_PASSED);
        sagaRepository.save(saga);

        // 4. Step 2 & 3: Debit Source and Credit Target with SAGA Compensation
        return executeDebitAndCredit(tx, saga);
    }

    /**
     * Resumes SAGA when user submits and verifies OTP for a flagged transaction.
     */
    public TransferResponse verifyOtpAndResumeSaga(String transactionId, String otpCode) {
        TransferTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BankingException("TX_NOT_FOUND", "Transaction not found: " + transactionId));

        if (tx.getStatus() != TransactionStatus.FRAUD_FLAGGED) {
            throw new BankingException("INVALID_STATE", "Transaction is not pending OTP verification (status: " + tx.getStatus() + ")");
        }

        boolean valid = fraudClient.verifyOtp(transactionId, otpCode);
        if (!valid) {
            throw new BankingException("INVALID_OTP", "Invalid or expired OTP entered");
        }

        log.info("OTP verified for transaction {}. Resuming SAGA money movement...", transactionId);
        tx.setOtpVerified(true);
        tx.setStatus(TransactionStatus.IN_PROGRESS);
        transactionRepository.save(tx);

        SagaInstance saga = sagaRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new BankingException("SAGA_NOT_FOUND", "Saga not found for tx: " + transactionId));

        saga.setStatus(SagaStatus.FRAUD_CHECK_PASSED);
        sagaRepository.save(saga);

        return executeDebitAndCredit(tx, saga);
    }

    /**
     * Executes the money movement steps (Debit -> Credit -> Compensate on failure).
     */
    private TransferResponse executeDebitAndCredit(TransferTransaction tx, SagaInstance saga) {
        long startTime = System.currentTimeMillis();

        // Step 2: Debit Source Account
        boolean debited = false;
        try {
            log.info("[SAGA {}] Executing Step: Debit Source Account {}", saga.getSagaId(), tx.getSourceAccountId());
            accountClient.debit(
                    tx.getSourceAccountId(),
                    tx.getAmount(),
                    tx.getId(),
                    saga.getSagaId(),
                    "Transfer to " + tx.getTargetAccountId()
            );
            debited = true;

            recordStepLog(saga.getSagaId(), "DEBIT_SOURCE_ACCOUNT", "SUCCESS",
                    "Successfully debited " + tx.getAmount() + " from " + tx.getSourceAccountId(),
                    System.currentTimeMillis() - startTime);

            saga.setStatus(SagaStatus.SOURCE_DEBITED);
            saga.setCurrentStep("DEBIT_SOURCE_ACCOUNT");
            sagaRepository.save(saga);

        } catch (Throwable e) {
            log.error("[SAGA {}] Debit failed: {}", saga.getSagaId(), e.getMessage());
            recordStepLog(saga.getSagaId(), "DEBIT_SOURCE_ACCOUNT", "FAILED", e.getMessage(), System.currentTimeMillis() - startTime);

            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureStep("DEBIT_SOURCE_ACCOUNT");
            tx.setFailureReason("Debit failed: " + e.getMessage());
            tx.setCompletedAt(Instant.now());
            transactionRepository.save(tx);

            saga.setStatus(SagaStatus.FAILED);
            saga.setFailureStep("DEBIT_SOURCE_ACCOUNT");
            saga.setFailureReason(e.getMessage());
            sagaRepository.save(saga);

            emitTransferFailedEvent(tx, saga, false);
            return mapToResponse(tx, "Transfer failed: " + e.getMessage());
        }

        // Step 3: Credit Beneficiary Account
        long creditStartTime = System.currentTimeMillis();
        try {
            log.info("[SAGA {}] Executing Step: Credit Beneficiary Account {}", saga.getSagaId(), tx.getTargetAccountId());
            accountClient.credit(
                    tx.getTargetAccountId(),
                    tx.getAmount(),
                    tx.getId(),
                    saga.getSagaId(),
                    "Transfer from " + tx.getSourceAccountId()
            );

            recordStepLog(saga.getSagaId(), "CREDIT_BENEFICIARY_ACCOUNT", "SUCCESS",
                    "Successfully credited " + tx.getAmount() + " to " + tx.getTargetAccountId(),
                    System.currentTimeMillis() - creditStartTime);

            saga.setStatus(SagaStatus.BENEFICIARY_CREDITED);
            saga.setCurrentStep("CREDIT_BENEFICIARY_ACCOUNT");

        } catch (Exception creditException) {
            // DOWNSTREAM FAILURE OCCURRED!
            // SAGA COMPENSATING TRANSACTION: Rollback source debit!
            log.error("[SAGA {}] Beneficiary credit failed: {}. TRIGGERING COMPENSATING TRANSACTION ROLLBACK!",
                    saga.getSagaId(), creditException.getMessage());

            recordStepLog(saga.getSagaId(), "CREDIT_BENEFICIARY_ACCOUNT", "FAILED",
                    creditException.getMessage(), System.currentTimeMillis() - creditStartTime);

            saga.setStatus(SagaStatus.COMPENSATION_TRIGGERED);
            saga.setFailureStep("CREDIT_BENEFICIARY_ACCOUNT");
            saga.setFailureReason("Beneficiary credit failed: " + creditException.getMessage());
            sagaRepository.save(saga);

            long compStartTime = System.currentTimeMillis();
            try {
                accountClient.compensateDebit(
                        tx.getSourceAccountId(),
                        tx.getAmount(),
                        tx.getId(),
                        saga.getSagaId(),
                        "Rollback debit: beneficiary credit failed"
                );

                recordStepLog(saga.getSagaId(), "COMPENSATE_SOURCE_DEBIT", "SUCCESS",
                        "Successfully refunded " + tx.getAmount() + " back to source account",
                        System.currentTimeMillis() - compStartTime);

                saga.setStatus(SagaStatus.COMPENSATED);
                sagaRepository.save(saga);

                tx.setStatus(TransactionStatus.COMPENSATED);
                tx.setFailureStep("CREDIT_BENEFICIARY_ACCOUNT");
                tx.setFailureReason("Target credit failed (" + creditException.getMessage() + ") - Source account refunded via SAGA compensation");
                tx.setCompletedAt(Instant.now());
                transactionRepository.save(tx);

                emitTransferFailedEvent(tx, saga, true);
                return mapToResponse(tx, "Transfer failed at destination, but funds were safely rolled back and refunded to your account");

            } catch (Exception compException) {
                log.error("[SAGA {}] CRITICAL: Compensation transaction failed! Manual intervention required: {}",
                        saga.getSagaId(), compException.getMessage());
                recordStepLog(saga.getSagaId(), "COMPENSATE_SOURCE_DEBIT", "FAILED",
                        compException.getMessage(), System.currentTimeMillis() - compStartTime);

                saga.setStatus(SagaStatus.FAILED);
                sagaRepository.save(saga);

                tx.setStatus(TransactionStatus.FAILED);
                tx.setFailureStep("COMPENSATION_CRITICAL_FAILURE");
                tx.setFailureReason("Compensation failed: " + compException.getMessage());
                transactionRepository.save(tx);

                emitTransferFailedEvent(tx, saga, false);
                return mapToResponse(tx, "Critical error during compensation: " + compException.getMessage());
            }
        }

        // Step 4: Transfer Completed Successfully!
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep("COMPLETED");
        sagaRepository.save(saga);

        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setCompletedAt(Instant.now());
        TransferTransaction completedTx = transactionRepository.save(tx);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[SAGA {}] Transfer {} COMPLETED successfully in {}ms", saga.getSagaId(), tx.getId(), totalTime);

        publishEvent(TRANSACTION_EVENTS_TOPIC, tx.getId(), TransferCompletedEvent.builder()
                .transactionId(tx.getId())
                .sagaId(saga.getSagaId())
                .sourceAccountId(tx.getSourceAccountId())
                .targetAccountId(tx.getTargetAccountId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .totalProcessingTimeMs(totalTime)
                .timestamp(Instant.now())
                .build());

        return mapToResponse(completedTx, "Money transferred successfully in " + totalTime + "ms");
    }

    private FraudEvaluationResult evaluateFraudStep(TransferTransaction tx, SagaInstance saga) {
        long startTime = System.currentTimeMillis();
        try {
            AccountDto sourceAcc = accountClient.getAccount(tx.getSourceAccountId());
            FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                    .transactionId(tx.getId())
                    .sourceAccountId(tx.getSourceAccountId())
                    .targetAccountId(tx.getTargetAccountId())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .currentBalance(sourceAcc.getBalance())
                    .deviceId(tx.getDeviceId())
                    .ipAddress(tx.getIpAddress())
                    .location(tx.getLocation())
                    .build();

            FraudEvaluationResult result = fraudClient.evaluate(req);
            recordStepLog(saga.getSagaId(), "FRAUD_EVALUATION", "SUCCESS",
                    "Score: " + result.getRiskScore() + ", Decision: " + result.getDecision(),
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Throwable e) {
            log.warn("Fraud service evaluation failed, proceeding with fallback: {}", e.getMessage());
            recordStepLog(saga.getSagaId(), "FRAUD_EVALUATION", "WARNING", e.getMessage(), System.currentTimeMillis() - startTime);
            return FraudEvaluationResult.builder()
                    .transactionId(tx.getId())
                    .decision(FraudDecision.APPROVED)
                    .riskScore(0)
                    .build();
        }
    }

    private void recordStepLog(String sagaId, String stepName, String status, String details, long durationMs) {
        SagaStepLog logEntry = SagaStepLog.builder()
                .id(UUID.randomUUID().toString())
                .sagaId(sagaId)
                .stepName(stepName)
                .status(status)
                .details(details)
                .durationMs(durationMs)
                .build();
        stepLogRepository.save(logEntry);
    }

    private void emitTransferFailedEvent(TransferTransaction tx, SagaInstance saga, boolean compensated) {
        publishEvent(TRANSACTION_EVENTS_TOPIC, tx.getId(), TransferFailedEvent.builder()
                .transactionId(tx.getId())
                .sagaId(saga.getSagaId())
                .sourceAccountId(tx.getSourceAccountId())
                .targetAccountId(tx.getTargetAccountId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .failureStage(tx.getFailureStep())
                .failureReason(tx.getFailureReason())
                .compensated(compensated)
                .timestamp(Instant.now())
                .build());
    }

    private void publishEvent(String topic, String key, Object event) {
        try {
            if (kafkaTemplate != null) {
                kafkaTemplate.send(topic, key, event);
            }
        } catch (Throwable e) {
            log.warn("Kafka event publishing skipped (Kafka broker offline): {}", e.getMessage());
        }
    }

    private TransferResponse mapToResponse(TransferTransaction tx, String message) {
        return TransferResponse.builder()
                .transactionId(tx.getId())
                .sagaId(tx.getSagaId())
                .status(tx.getStatus())
                .sourceAccountId(tx.getSourceAccountId())
                .targetAccountId(tx.getTargetAccountId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .fraudRiskScore(tx.getFraudScore())
                .message(message)
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
