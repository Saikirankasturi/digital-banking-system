package com.digitalbanking.transaction.controller;

import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.common.dto.TransferRequest;
import com.digitalbanking.common.dto.TransferResponse;
import com.digitalbanking.common.exception.BankingException;
import com.digitalbanking.transaction.domain.SagaStepLog;
import com.digitalbanking.transaction.domain.TransferTransaction;
import com.digitalbanking.transaction.repository.SagaStepLogRepository;
import com.digitalbanking.transaction.repository.TransferTransactionRepository;
import com.digitalbanking.transaction.service.TransferSagaOrchestrator;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransactionController {

    private final TransferSagaOrchestrator sagaOrchestrator;
    private final TransferTransactionRepository transactionRepository;
    private final SagaStepLogRepository stepLogRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody TransferRequest request) {

        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }

        TransferResponse response = sagaOrchestrator.executeTransferSaga(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(response, response.getMessage()));
    }

    @PostMapping("/{transactionId}/verify-otp")
    public ResponseEntity<ApiResponse<TransferResponse>> verifyOtp(
            @PathVariable String transactionId,
            @RequestBody OtpSubmitRequest request) {

        TransferResponse response = sagaOrchestrator.verifyOtpAndResumeSaga(transactionId, request.getOtpCode());
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferTransaction>> getTransaction(@PathVariable String id) {
        TransferTransaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new BankingException("TX_NOT_FOUND", "Transaction not found: " + id));
        return ResponseEntity.ok(ApiResponse.success(tx));
    }

    @GetMapping("/saga/{sagaId}/audit")
    public ResponseEntity<ApiResponse<List<SagaStepLog>>> getSagaAuditLog(@PathVariable String sagaId) {
        List<SagaStepLog> logs = stepLogRepository.findBySagaIdOrderByTimestampAsc(sagaId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<List<TransferTransaction>>> getAccountTransactions(@PathVariable String accountId) {
        List<TransferTransaction> txs = transactionRepository.findBySourceAccountIdOrderByCreatedAtDesc(accountId);
        return ResponseEntity.ok(ApiResponse.success(txs));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpSubmitRequest {
        private String otpCode;
    }
}
