package com.digitalbanking.fraud.kafka;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import com.digitalbanking.common.dto.FraudEvaluationResult;
import com.digitalbanking.common.events.TransferInitiatedEvent;
import com.digitalbanking.fraud.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudEventConsumer {

    private final FraudDetectionService fraudDetectionService;

    @KafkaListener(topics = "transfer-initiated", groupId = "fraud-detection-service-group")
    public void onTransferInitiated(TransferInitiatedEvent event) {
        log.info("Kafka: Received transfer initiated event for fraud analysis: tx={}", event.getTransactionId());

        FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                .transactionId(event.getTransactionId())
                .sourceAccountId(event.getSourceAccountId())
                .targetAccountId(event.getTargetAccountId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .deviceId(event.getDeviceId())
                .ipAddress(event.getIpAddress())
                .location(event.getLocation())
                .build();

        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(req);
        log.info("Asynchronous fraud stream check completed: tx={}, score={}, decision={}",
                event.getTransactionId(), result.getRiskScore(), result.getDecision());
    }
}
