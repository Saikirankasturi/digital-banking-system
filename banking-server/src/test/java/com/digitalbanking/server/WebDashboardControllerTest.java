package com.digitalbanking.server;

import com.digitalbanking.account.domain.LedgerEntry;
import com.digitalbanking.account.service.AccountService;
import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.common.enums.AccountStatus;
import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.TransactionType;
import com.digitalbanking.server.controller.WebDashboardController;
import com.digitalbanking.transaction.domain.TransferTransaction;
import com.digitalbanking.transaction.repository.TransferTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebDashboardController.class)
@ContextConfiguration(classes = WebDashboardController.class)
@Import({ThymeleafAutoConfiguration.class, WebMvcAutoConfiguration.class})
@AutoConfigureMockMvc
class WebDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private TransferTransactionRepository transactionRepository;

    @Test
    @DisplayName("GET / should render Thymeleaf index template with complete model attributes")
    void testGetDashboard_Success() throws Exception {
        AccountDto alice = AccountDto.builder()
                .id("acc-alice-uuid")
                .accountNumber("1001234567")
                .accountHolderName("Alice Smith")
                .balance(new BigDecimal("5000.00"))
                .currency(Currency.USD)
                .status(AccountStatus.ACTIVE)
                .build();

        AccountDto bob = AccountDto.builder()
                .id("acc-bob-uuid")
                .accountNumber("2001234567")
                .accountHolderName("Bob Jones")
                .balance(new BigDecimal("2500.00"))
                .currency(Currency.USD)
                .status(AccountStatus.ACTIVE)
                .build();

        LedgerEntry entry = LedgerEntry.builder()
                .id("led-1")
                .accountId("acc-alice-uuid")
                .transactionId("tx-1")
                .entryType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("5000.00"))
                .balanceBefore(BigDecimal.ZERO)
                .balanceAfter(new BigDecimal("5000.00"))
                .description("Initial opening deposit")
                .createdAt(Instant.now())
                .build();

        when(accountService.getAllAccounts()).thenReturn(List.of(alice, bob));
        when(accountService.getTotalLiquidity()).thenReturn(new BigDecimal("7500.00"));
        when(accountService.getAllRecentLedgerEntries()).thenReturn(List.of(entry));
        when(transactionRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("accounts", "accountCount", "totalLiquidity", "recentLedgers", "systemStatus"))
                .andExpect(model().attribute("accountCount", 2))
                .andExpect(model().attribute("defaultSourceAccountId", "acc-alice-uuid"))
                .andExpect(model().attribute("defaultTargetAccountId", "acc-bob-uuid"))
                .andExpect(model().attribute("systemStatus", "OPERATIONAL"))
                .andExpect(content().string(containsString("APEX DIGITAL BANK")))
                .andExpect(content().string(containsString("Thymeleaf SSR")));
    }

    @Test
    @DisplayName("GET /dashboard with empty database should render gracefully")
    void testGetDashboard_EmptyState() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(Collections.emptyList());
        when(accountService.getTotalLiquidity()).thenReturn(BigDecimal.ZERO);
        when(accountService.getAllRecentLedgerEntries()).thenReturn(Collections.emptyList());
        when(transactionRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("accountCount", 0))
                .andExpect(model().attribute("totalLiquidity", BigDecimal.ZERO))
                .andExpect(content().string(containsString("APEX DIGITAL BANK")));
    }
}
