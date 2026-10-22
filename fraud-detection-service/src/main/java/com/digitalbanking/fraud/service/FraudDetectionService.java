package com.digitalbanking.fraud.service;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import com.digitalbanking.common.dto.FraudEvaluationResult;
import com.digitalbanking.common.enums.FraudDecision;
import com.digitalbanking.common.events.FraudAlertEvent;
import com.digitalbanking.fraud.engine.FraudRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudRuleEngine ruleEngine;
    private final OtpService otpService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String FRAUD_ALERTS_TOPIC = "fraud-alerts";

    public FraudEvaluationResult evaluateTransaction(FraudEvaluationRequest request) {
        FraudEvaluationResult result = ruleEngine.evaluate(request);

        // If flagged for review, trigger OTP generation
        if (result.getDecision() == FraudDecision.REVIEW_FLAGGED) {
            String otp = otpService.generateOtp(request.getTransactionId());
            publishFraudAlert(request, result, "Transaction flagged for OTP challenge: " + String.join(", ", result.getTriggeredRules()));
        } else if (result.getDecision() == FraudDecision.REJECTED) {
            publishFraudAlert(request, result, "Transaction blocked outright due to high risk score: " + result.getRiskScore());
        }

        return result;
    }

    public boolean verifyOtp(String transactionId, String otpCode) {
        return otpService.verifyOtp(transactionId, otpCode);
    }

    public String generateOtpForTransaction(String transactionId) {
        return otpService.generateOtp(transactionId);
    }

    private void publishFraudAlert(FraudEvaluationRequest request, FraudEvaluationResult result, String note) {
        try {
            FraudAlertEvent alert = FraudAlertEvent.builder()
                    .alertId(UUID.randomUUID().toString())
                    .transactionId(request.getTransactionId())
                    .sourceAccountId(request.getSourceAccountId())
                    .targetAccountId(request.getTargetAccountId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .decision(result.getDecision())
                    .riskScore(result.getRiskScore())
                    .triggeredRules(result.getTriggeredRules())
                    .ipAddress(request.getIpAddress())
                    .deviceId(request.getDeviceId())
                    .location(request.getLocation())
                    .alertTimestamp(Instant.now())
                    .build();

            if (kafkaTemplate != null) {
                kafkaTemplate.send(FRAUD_ALERTS_TOPIC, request.getTransactionId(), alert);
                log.info("Published FraudAlertEvent to topic {}: {}", FRAUD_ALERTS_TOPIC, note);
            }
        } catch (Exception e) {
            log.warn("Could not publish fraud alert to Kafka (broker offline): {}", e.getMessage());
        }
    }
}
