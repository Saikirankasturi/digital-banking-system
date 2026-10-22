package com.digitalbanking.server.init;

import com.digitalbanking.account.repository.AccountRepository;
import com.digitalbanking.account.service.AccountService;
import com.digitalbanking.common.dto.CreateAccountRequest;
import com.digitalbanking.common.enums.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final AccountService accountService;
    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        if (accountRepository.count() == 0) {
            log.info("Bootstrapping Digital Banking System demo accounts for Thymeleaf & SAGA...");

            accountService.createAccount(CreateAccountRequest.builder()
                    .accountHolderName("Alice Smith")
                    .email("alice@apexbank.com")
                    .initialDeposit(new BigDecimal("5000.00"))
                    .currency(Currency.USD)
                    .build());

            accountService.createAccount(CreateAccountRequest.builder()
                    .accountHolderName("Bob Jones")
                    .email("bob@apexbank.com")
                    .initialDeposit(new BigDecimal("2500.00"))
                    .currency(Currency.USD)
                    .build());

            accountService.createAccount(CreateAccountRequest.builder()
                    .accountHolderName("Charlie Davis")
                    .email("charlie@apexbank.com")
                    .initialDeposit(new BigDecimal("1200.00"))
                    .currency(Currency.USD)
                    .build());

            accountService.createAccount(CreateAccountRequest.builder()
                    .accountHolderName("Vault Reserve Account")
                    .email("treasury@apexbank.com")
                    .initialDeposit(new BigDecimal("50000.00"))
                    .currency(Currency.USD)
                    .build());

            log.info("Demo accounts seeded successfully. Total accounts: {}", accountRepository.count());
        } else {
            log.info("Accounts already present ({}), skipping demo bootstrap.", accountRepository.count());
        }
    }
}
