package com.digitalbanking.payment.client;

import com.digitalbanking.common.dto.AccountDto;
import com.digitalbanking.common.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Slf4j
@Component
public class AccountClient {

    private final RestTemplate restTemplate;
    private final String accountServiceUrl;

    public AccountClient(@Value("${services.account.url:http://localhost:8081}") String accountServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.accountServiceUrl = accountServiceUrl;
    }

    public AccountDto credit(String accountId, BigDecimal amount, String transactionId, String description) {
        String url = accountServiceUrl + "/api/v1/accounts/" + accountId + "/credit";
        DebitCreditPayload payload = new DebitCreditPayload(amount, transactionId, "PAYMENT_GATEWAY", description);
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
        throw new RuntimeException("Failed to credit account via Account Service");
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
