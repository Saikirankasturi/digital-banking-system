package com.digitalbanking.server.controller;

import com.digitalbanking.account.domain.LedgerEntry;
import com.digitalbanking.account.service.AccountService;
import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.transaction.domain.TransferTransaction;
import com.digitalbanking.transaction.repository.TransferTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * Thymeleaf Web Controller delivering Server-Side Rendered (SSR) views
 * with live domain models, reactive components, and system telemetry.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebDashboardController {

    private final AccountService accountService;
    private final TransferTransactionRepository transactionRepository;

    @Value("${server.port:8080}")
    private String serverPort;

    @GetMapping({"/", "/dashboard"})
    public String getDashboard(
            @RequestParam(value = "tab", required = false, defaultValue = "overview") String activeTab,
            Model model) {

        List<AccountDto> accounts = accountService.getAllAccounts();
        BigDecimal totalLiquidity = accountService.getTotalLiquidity();
        List<LedgerEntry> recentLedgers = accountService.getAllRecentLedgerEntries();
        List<TransferTransaction> recentTransactions = transactionRepository.findTop50ByOrderByCreatedAtDesc();

        model.addAttribute("accounts", accounts);
        model.addAttribute("accountCount", accounts.size());
        model.addAttribute("totalLiquidity", totalLiquidity != null ? totalLiquidity : BigDecimal.ZERO);
        model.addAttribute("recentLedgers", recentLedgers);
        model.addAttribute("recentTransactions", recentTransactions);
        model.addAttribute("ledgerCount", recentLedgers.size());
        model.addAttribute("txCount", recentTransactions.size());
        model.addAttribute("systemStatus", "OPERATIONAL");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("serverPort", serverPort);
        model.addAttribute("h2ConsoleUrl", "/h2-console");

        if (!accounts.isEmpty()) {
            model.addAttribute("defaultSourceAccountId", accounts.get(0).getId());
            if (accounts.size() > 1) {
                model.addAttribute("defaultTargetAccountId", accounts.get(1).getId());
            } else {
                model.addAttribute("defaultTargetAccountId", accounts.get(0).getId());
            }
        }

        return "index";
    }
}
