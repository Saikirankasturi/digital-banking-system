package com.digitalbanking.account.service;

import com.digitalbanking.account.domain.Account;
import com.digitalbanking.account.domain.LedgerEntry;
import com.digitalbanking.account.repository.AccountRepository;
import com.digitalbanking.account.repository.LedgerRepository;
import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.common.dto.CreateAccountRequest;
import com.digitalbanking.common.enums.AccountStatus;
import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.TransactionType;
import com.digitalbanking.common.exception.InsufficientFundsException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;

    @BeforeEach
    void setUp() {
        sampleAccount = Account.builder()
                .id("acc-123")
                .accountNumber("1234567890")
                .accountHolderName("Alice Smith")
                .email("alice@example.com")
                .balance(new BigDecimal("1000.00"))
                .reservedBalance(BigDecimal.ZERO)
                .currency(Currency.USD)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Create account: Saves account and records initial deposit ledger entry")
    void testCreateAccount() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountHolderName("Alice Smith")
                .email("alice@example.com")
                .initialDeposit(new BigDecimal("1000.00"))
                .currency(Currency.USD)
                .build();

        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountDto dto = accountService.createAccount(request);

        assertNotNull(dto);
        assertEquals(new BigDecimal("1000.00"), dto.getBalance());
        assertEquals("Alice Smith", dto.getAccountHolderName());
        verify(ledgerRepository, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Debit account: Deducts balance and records TRANSFER ledger entry")
    void testDebitAccountSuccess() {
        when(accountRepository.findByIdWithLock("acc-123")).thenReturn(Optional.of(sampleAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountDto updated = accountService.debitAccount(
                "acc-123", new BigDecimal("200.00"), "tx-1", "saga-1", "Test debit");

        assertEquals(new BigDecimal("800.00"), updated.getBalance());
        verify(ledgerRepository, times(1)).save(argThat(entry ->
                entry.getEntryType() == TransactionType.TRANSFER &&
                        entry.getAmount().compareTo(new BigDecimal("200.00")) == 0 &&
                        entry.getBalanceAfter().compareTo(new BigDecimal("800.00")) == 0
        ));
    }

    @Test
    @DisplayName("Debit account: Throws InsufficientFundsException when balance is too low")
    void testDebitInsufficientFunds() {
        when(accountRepository.findByIdWithLock("acc-123")).thenReturn(Optional.of(sampleAccount));

        assertThrows(InsufficientFundsException.class, () ->
                accountService.debitAccount("acc-123", new BigDecimal("2000.00"), "tx-1", "saga-1", "Overdraft test")
        );

        verify(accountRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("SAGA Compensating Transaction: Restores balance and writes COMPENSATION ledger entry")
    void testCompensateDebit() {
        // Initial state before compensation: $800
        sampleAccount.setBalance(new BigDecimal("800.00"));

        when(accountRepository.findByIdWithLock("acc-123")).thenReturn(Optional.of(sampleAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountDto compensated = accountService.compensateDebit(
                "acc-123", new BigDecimal("200.00"), "tx-1", "saga-1", "Rollback due to downstream failure");

        // Balance restored back to $1000.00!
        assertEquals(new BigDecimal("1000.00"), compensated.getBalance());

        verify(ledgerRepository, times(1)).save(argThat(entry ->
                entry.getEntryType() == TransactionType.COMPENSATION &&
                        entry.getAmount().compareTo(new BigDecimal("200.00")) == 0 &&
                        entry.getBalanceAfter().compareTo(new BigDecimal("1000.00")) == 0
        ));
    }
}
