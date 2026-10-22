package com.digitalbanking.transaction.service;

import com.digitalbanking.common.dto.*;
import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.FraudDecision;
import com.digitalbanking.common.enums.TransactionStatus;
import com.digitalbanking.common.exception.BankingException;
import com.digitalbanking.transaction.client.AccountClient;
import com.digitalbanking.transaction.client.FraudClient;
import com.digitalbanking.transaction.domain.SagaInstance;
import com.digitalbanking.transaction.domain.SagaStepLog;
import com.digitalbanking.transaction.domain.TransferTransaction;
import com.digitalbanking.transaction.repository.SagaInstanceRepository;
import com.digitalbanking.transaction.repository.SagaStepLogRepository;
import com.digitalbanking.transaction.repository.TransferTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferSagaOrchestratorTest {

    @Mock
    private TransferTransactionRepository transactionRepository;

    @Mock
    private SagaInstanceRepository sagaRepository;

    @Mock
    private SagaStepLogRepository stepLogRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private AccountClient accountClient;

    @Mock
    private FraudClient fraudClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TransferSagaOrchestrator sagaOrchestrator;

    private TransferRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = TransferRequest.builder()
                .sourceAccountId("ACC-SOURCE")
                .targetAccountId("ACC-BENEFICIARY")
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .idempotencyKey("idem-test-key-001")
                .build();

        lenient().when(idempotencyService.acquireLock(any(), any())).thenReturn(true);
        lenient().when(transactionRepository.save(any(TransferTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sagaRepository.save(any(SagaInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stepLogRepository.save(any(SagaStepLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("SAGA Happy Path: Clean transfer executes in milliseconds and completes with SUCCESS")
    void testSuccessfulTransferSaga() {
        // Mock source account
        AccountDto sourceAccount = AccountDto.builder()
                .id("ACC-SOURCE")
                .balance(new BigDecimal("1000.00"))
                .build();
        when(accountClient.getAccount("ACC-SOURCE")).thenReturn(sourceAccount);

        // Mock clean fraud check
        when(fraudClient.evaluate(any())).thenReturn(
                FraudEvaluationResult.builder()
                        .decision(FraudDecision.APPROVED)
                        .riskScore(10)
                        .build()
        );

        // Execute SAGA
        TransferResponse response = sagaOrchestrator.executeTransferSaga(sampleRequest);

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());

        // Verify debit and credit were both executed
        verify(accountClient, times(1)).debit(eq("ACC-SOURCE"), eq(new BigDecimal("100.00")), any(), any(), any());
        verify(accountClient, times(1)).credit(eq("ACC-BENEFICIARY"), eq(new BigDecimal("100.00")), any(), any(), any());
        // Verify compensation was NOT called
        verify(accountClient, never()).compensateDebit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Real-Time Fraud Prevention: High risk transfer is rejected BEFORE any money is debited")
    void testFraudRejectedTransferSaga() {
        AccountDto sourceAccount = AccountDto.builder()
                .id("ACC-SOURCE")
                .balance(new BigDecimal("1000.00"))
                .build();
        when(accountClient.getAccount("ACC-SOURCE")).thenReturn(sourceAccount);

        // Mock fraudulent transfer (e.g. blacklisted receiver)
        when(fraudClient.evaluate(any())).thenReturn(
                FraudEvaluationResult.builder()
                        .decision(FraudDecision.REJECTED)
                        .riskScore(100)
                        .build()
        );

        TransferResponse response = sagaOrchestrator.executeTransferSaga(sampleRequest);

        assertNotNull(response);
        assertEquals(TransactionStatus.FRAUD_REJECTED, response.getStatus());

        // Money NEVER touched!
        verify(accountClient, never()).debit(any(), any(), any(), any(), any());
        verify(accountClient, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SAGA Compensation: When beneficiary credit fails, source debit is rolled back and refunded")
    void testSagaCompensationRollbackWhenCreditFails() {
        AccountDto sourceAccount = AccountDto.builder()
                .id("ACC-SOURCE")
                .balance(new BigDecimal("1000.00"))
                .build();
        when(accountClient.getAccount("ACC-SOURCE")).thenReturn(sourceAccount);

        when(fraudClient.evaluate(any())).thenReturn(
                FraudEvaluationResult.builder()
                        .decision(FraudDecision.APPROVED)
                        .riskScore(5)
                        .build()
        );

        // Debit succeeds
        when(accountClient.debit(eq("ACC-SOURCE"), any(), any(), any(), any()))
                .thenReturn(sourceAccount);

        // Downstream beneficiary credit fails (e.g. Beneficiary account frozen or closed)
        when(accountClient.credit(eq("ACC-BENEFICIARY"), any(), any(), any(), any()))
                .thenThrow(new BankingException("ACCOUNT_INACTIVE", "Beneficiary account is frozen"));

        // Execute SAGA
        TransferResponse response = sagaOrchestrator.executeTransferSaga(sampleRequest);

        assertNotNull(response);
        // Transfer should be COMPENSATED (safe rollback)
        assertEquals(TransactionStatus.COMPENSATED, response.getStatus());

        // Verify compensating transaction was triggered on source account!
        verify(accountClient, times(1)).compensateDebit(
                eq("ACC-SOURCE"),
                eq(new BigDecimal("100.00")),
                any(),
                any(),
                contains("Rollback debit: beneficiary credit failed")
        );
    }
}
