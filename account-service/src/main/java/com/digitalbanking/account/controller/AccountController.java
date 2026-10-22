package com.digitalbanking.account.controller;

import com.digitalbanking.account.domain.LedgerEntry;
import com.digitalbanking.account.service.AccountService;
import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.common.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountDto created = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Account opened successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDto>>> getAllAccounts() {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAllAccounts()));
    }

    @GetMapping("/ledger/recent")
    public ResponseEntity<ApiResponse<List<LedgerEntry>>> getRecentLedgers() {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAllRecentLedgerEntries()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDto>> getAccount(@PathVariable("id") String id) {
        AccountDto account = accountService.getAccount(id);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountDto>> getAccountByNumber(@PathVariable("accountNumber") String accountNumber) {
        AccountDto account = accountService.getAccountByNumber(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<ApiResponse<AccountDto>> debitAccount(
            @PathVariable("id") String id,
            @RequestBody DebitCreditOperationRequest request) {
        AccountDto updated = accountService.debitAccount(
                id, request.getAmount(), request.getTransactionId(), request.getSagaId(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.success(updated, "Account debited successfully"));
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<ApiResponse<AccountDto>> creditAccount(
            @PathVariable("id") String id,
            @RequestBody DebitCreditOperationRequest request) {
        AccountDto updated = accountService.creditAccount(
                id, request.getAmount(), request.getTransactionId(), request.getSagaId(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.success(updated, "Account credited successfully"));
    }

    @PostMapping("/{id}/compensate")
    public ResponseEntity<ApiResponse<AccountDto>> compensateDebit(
            @PathVariable("id") String id,
            @RequestBody DebitCreditOperationRequest request) {
        AccountDto updated = accountService.compensateDebit(
                id, request.getAmount(), request.getTransactionId(), request.getSagaId(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.success(updated, "Debit compensated / refunded successfully"));
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<ApiResponse<List<LedgerEntry>>> getLedger(@PathVariable("id") String id) {
        List<LedgerEntry> entries = accountService.getAccountLedger(id);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebitCreditOperationRequest {
        private BigDecimal amount;
        private String transactionId;
        private String sagaId;
        private String description;
    }
}
