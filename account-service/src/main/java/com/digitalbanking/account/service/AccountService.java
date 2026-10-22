package com.digitalbanking.account.service;

import com.digitalbanking.account.domain.Account;
import com.digitalbanking.account.domain.LedgerEntry;
import com.digitalbanking.account.repository.AccountRepository;
import com.digitalbanking.account.repository.LedgerRepository;
import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.common.dto.CreateAccountRequest;
import com.digitalbanking.common.enums.AccountStatus;
import com.digitalbanking.common.enums.TransactionType;
import com.digitalbanking.common.events.AccountCreditedEvent;
import com.digitalbanking.common.events.AccountDebitedEvent;
import com.digitalbanking.common.events.DebitCompensationEvent;
import com.digitalbanking.common.exception.AccountFrozenException;
import com.digitalbanking.common.exception.AccountNotFoundException;
import com.digitalbanking.common.exception.InsufficientFundsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ACCOUNT_EVENTS_TOPIC = "account-events";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request) {
        String accountNumber = generateUniqueAccountNumber();
        BigDecimal initialDeposit = request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO;

        Account account = Account.builder()
                .id(UUID.randomUUID().toString())
                .accountNumber(accountNumber)
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .balance(initialDeposit)
                .reservedBalance(BigDecimal.ZERO)
                .currency(request.getCurrency())
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);

        if (initialDeposit.compareTo(BigDecimal.ZERO) > 0) {
            recordLedgerEntry(
                    saved.getId(),
                    "INITIAL_DEPOSIT_" + saved.getId(),
                    TransactionType.DEPOSIT,
                    initialDeposit,
                    BigDecimal.ZERO,
                    initialDeposit,
                    "Initial account opening deposit"
            );
        }

        log.info("Created account {} for {} with initial balance {}",
                saved.getAccountNumber(), saved.getAccountHolderName(), saved.getBalance());

        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return mapToDto(account);
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("accountNumber: " + accountNumber));
        return mapToDto(account);
    }

    /**
     * SAGA Step: Debit source account with pessimistic locking.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AccountDto debitAccount(String accountId, BigDecimal amount, String transactionId, String sagaId, String description) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException(accountId, account.getStatus().name());
        }

        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId, account.getAvailableBalance().toPlainString(), amount.toPlainString());
        }

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        account.setBalance(balanceAfter);
        Account updated = accountRepository.save(account);

        recordLedgerEntry(
                account.getId(),
                transactionId,
                TransactionType.TRANSFER,
                amount,
                balanceBefore,
                balanceAfter,
                description != null ? description : "Debit transfer transaction: " + transactionId
        );

        log.info("[SAGA {}] Debited {} from account {}. New balance: {}", sagaId, amount, accountId, balanceAfter);

        publishEvent(ACCOUNT_EVENTS_TOPIC, transactionId, AccountDebitedEvent.builder()
                .transactionId(transactionId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(amount)
                .currency(account.getCurrency())
                .remainingBalance(balanceAfter)
                .timestamp(Instant.now())
                .build());

        return mapToDto(updated);
    }

    /**
     * SAGA Step: Credit beneficiary account with pessimistic locking.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AccountDto creditAccount(String accountId, BigDecimal amount, String transactionId, String sagaId, String description) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException(accountId, account.getStatus().name());
        }

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        account.setBalance(balanceAfter);
        Account updated = accountRepository.save(account);

        recordLedgerEntry(
                account.getId(),
                transactionId,
                TransactionType.TRANSFER,
                amount,
                balanceBefore,
                balanceAfter,
                description != null ? description : "Credit transfer transaction: " + transactionId
        );

        log.info("[SAGA {}] Credited {} to account {}. New balance: {}", sagaId, amount, accountId, balanceAfter);

        publishEvent(ACCOUNT_EVENTS_TOPIC, transactionId, AccountCreditedEvent.builder()
                .transactionId(transactionId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(amount)
                .currency(account.getCurrency())
                .newBalance(balanceAfter)
                .timestamp(Instant.now())
                .build());

        return mapToDto(updated);
    }

    /**
     * SAGA Compensation Step: Reverses debit when downstream step fails.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AccountDto compensateDebit(String accountId, BigDecimal amount, String transactionId, String sagaId, String reason) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        account.setBalance(balanceAfter);
        Account updated = accountRepository.save(account);

        recordLedgerEntry(
                account.getId(),
                transactionId,
                TransactionType.COMPENSATION,
                amount,
                balanceBefore,
                balanceAfter,
                "SAGA Compensation Rollback: " + reason
        );

        log.warn("[SAGA {}] COMPENSATED debit of {} for account {}. Balance restored to {}",
                sagaId, amount, accountId, balanceAfter);

        publishEvent(ACCOUNT_EVENTS_TOPIC, transactionId, DebitCompensationEvent.builder()
                .transactionId(transactionId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(amount)
                .currency(account.getCurrency())
                .reason(reason)
                .timestamp(Instant.now())
                .build());

        return mapToDto(updated);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountLedger(String accountId) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAllRecentLedgerEntries() {
        return ledgerRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalLiquidity() {
        return accountRepository.findAll().stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recordLedgerEntry(String accountId, String transactionId, TransactionType type,
                                   BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   String description) {
        LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(description)
                .build();
        ledgerRepository.save(entry);
    }

    private String generateUniqueAccountNumber() {
        String number;
        do {
            long rand = 1000000000L + (long) (RANDOM.nextDouble() * 9000000000L);
            number = String.valueOf(rand);
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }

    private void publishEvent(String topic, String key, Object event) {
        try {
            if (kafkaTemplate != null) {
                kafkaTemplate.send(topic, key, event);
            }
        } catch (Exception e) {
            log.warn("Kafka event publishing skipped (Kafka broker offline or not configured): {}", e.getMessage());
        }
    }

    private AccountDto mapToDto(Account a) {
        return AccountDto.builder()
                .id(a.getId())
                .accountNumber(a.getAccountNumber())
                .accountHolderName(a.getAccountHolderName())
                .email(a.getEmail())
                .balance(a.getBalance())
                .reservedBalance(a.getReservedBalance())
                .currency(a.getCurrency())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
