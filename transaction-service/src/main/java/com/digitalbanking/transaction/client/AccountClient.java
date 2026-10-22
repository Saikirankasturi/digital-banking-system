package com.digitalbanking.transaction.client;

import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.common.exception.BankingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Slf4j
@Component
public class AccountClient {

    private final RestTemplate restTemplate;
    private final String accountServiceUrl;

    public AccountClient(RestTemplate restTemplate,
                         @Value("${services.account.url:http://localhost:8081}") String accountServiceUrl) {
        this.restTemplate = restTemplate;
        this.accountServiceUrl = accountServiceUrl;
    }

    public AccountDto getAccount(String accountId) {
        try {
            String url = accountServiceUrl + "/api/v1/accounts/" + accountId;
            ResponseEntity<ApiResponse<AccountDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<AccountDto>>() {}
            );
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new BankingException("ACCOUNT_FETCH_ERROR", "Failed to fetch account " + accountId);
        } catch (HttpClientErrorException e) {
            throw new BankingException("ACCOUNT_SERVICE_ERROR", "Account error: " + e.getResponseBodyAsString(), e);
        }
    }

    public AccountDto debit(String accountId, BigDecimal amount, String transactionId, String sagaId, String reason) {
        try {
            String url = accountServiceUrl + "/api/v1/accounts/" + accountId + "/debit";
            DebitCreditPayload payload = new DebitCreditPayload(amount, transactionId, sagaId, reason);
            HttpEntity<DebitCreditPayload> entity = new HttpEntity<>(payload);

            ResponseEntity<ApiResponse<AccountDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<AccountDto>>() {}
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new BankingException("DEBIT_FAILED", "Failed to debit account: " + accountId);
        } catch (HttpClientErrorException e) {
            log.error("Debit failed on account {}: {}", accountId, e.getResponseBodyAsString());
            throw new BankingException("DEBIT_FAILED", e.getResponseBodyAsString(), e);
        }
    }

    public AccountDto credit(String accountId, BigDecimal amount, String transactionId, String sagaId, String reason) {
        try {
            String url = accountServiceUrl + "/api/v1/accounts/" + accountId + "/credit";
            DebitCreditPayload payload = new DebitCreditPayload(amount, transactionId, sagaId, reason);
            HttpEntity<DebitCreditPayload> entity = new HttpEntity<>(payload);

            ResponseEntity<ApiResponse<AccountDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<AccountDto>>() {}
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new BankingException("CREDIT_FAILED", "Failed to credit account: " + accountId);
        } catch (HttpClientErrorException e) {
            log.error("Credit failed on account {}: {}", accountId, e.getResponseBodyAsString());
            throw new BankingException("CREDIT_FAILED", e.getResponseBodyAsString(), e);
        }
    }

    public AccountDto compensateDebit(String accountId, BigDecimal amount, String transactionId, String sagaId, String reason) {
        try {
            String url = accountServiceUrl + "/api/v1/accounts/" + accountId + "/compensate";
            DebitCreditPayload payload = new DebitCreditPayload(amount, transactionId, sagaId, reason);
            HttpEntity<DebitCreditPayload> entity = new HttpEntity<>(payload);

            ResponseEntity<ApiResponse<AccountDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<AccountDto>>() {}
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new BankingException("COMPENSATION_FAILED", "Failed to compensate debit for account: " + accountId);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to compensate debit on account {}: {}", accountId, e.getMessage());
            throw new BankingException("COMPENSATION_FAILED", "Critical compensation failure: " + e.getMessage(), e);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebitCreditPayload {
        private BigDecimal amount;
        private String transactionId;
        private String sagaId;
        private String description;
    }
}
