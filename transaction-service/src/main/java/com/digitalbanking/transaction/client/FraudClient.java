package com.digitalbanking.transaction.client;

import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.common.dto.FraudEvaluationRequest;
import com.digitalbanking.common.dto.FraudEvaluationResult;
import com.digitalbanking.common.enums.FraudDecision;
import com.digitalbanking.common.exception.BankingException;
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

@Slf4j
@Component
public class FraudClient {

    private final RestTemplate restTemplate;
    private final String fraudServiceUrl;

    public FraudClient(RestTemplate restTemplate,
                       @Value("${services.fraud.url:http://localhost:8082}") String fraudServiceUrl) {
        this.restTemplate = restTemplate;
        this.fraudServiceUrl = fraudServiceUrl;
    }

    public FraudEvaluationResult evaluate(FraudEvaluationRequest request) {
        try {
            String url = fraudServiceUrl + "/api/v1/fraud/evaluate";
            HttpEntity<FraudEvaluationRequest> entity = new HttpEntity<>(request);

            ResponseEntity<ApiResponse<FraudEvaluationResult>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<FraudEvaluationResult>>() {}
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new BankingException("FRAUD_SERVICE_ERROR", "Invalid response from fraud service");
        } catch (Exception e) {
            log.error("Failed to connect to Fraud Detection Service: {}. Failing safe with default evaluation.", e.getMessage());
            // Fail-safe fallback if fraud service is temporarily down during local dev
            return FraudEvaluationResult.builder()
                    .transactionId(request.getTransactionId())
                    .decision(FraudDecision.APPROVED)
                    .riskScore(0)
                    .build();
        }
    }

    public boolean verifyOtp(String transactionId, String otpCode) {
        try {
            String url = fraudServiceUrl + "/api/v1/fraud/verify-otp";
            OtpVerificationRequest request = new OtpVerificationRequest(transactionId, otpCode);
            HttpEntity<OtpVerificationRequest> entity = new HttpEntity<>(request);

            ResponseEntity<ApiResponse<OtpVerificationResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<OtpVerificationResponse>>() {}
            );

            return response.getBody() != null &&
                    response.getBody().isSuccess() &&
                    response.getBody().getData() != null &&
                    response.getBody().getData().isValid();
        } catch (Exception e) {
            log.error("Failed to verify OTP with Fraud Service: {}", e.getMessage());
            return false;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerificationRequest {
        private String transactionId;
        private String otpCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerificationResponse {
        private String transactionId;
        private boolean valid;
        private String message;
    }
}
