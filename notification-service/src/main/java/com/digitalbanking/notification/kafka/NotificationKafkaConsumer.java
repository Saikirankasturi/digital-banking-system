package com.digitalbanking.notification.kafka;

import com.digitalbanking.common.events.DebitCompensationEvent;
import com.digitalbanking.common.events.FraudAlertEvent;
import com.digitalbanking.common.events.TransferCompletedEvent;
import com.digitalbanking.common.events.TransferFailedEvent;
import com.digitalbanking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "fraud-alerts", groupId = "notification-service-group")
    public void onFraudAlert(FraudAlertEvent event) {
        log.warn("Received Fraud Alert: score={}, tx={}", event.getRiskScore(), event.getTransactionId());

        String title = "⚠️ SECURITY ALERT: Suspicious Banking Activity";
        String body = String.format(
                "A transfer of %s %s from your account was flagged (Risk Score: %d/100). Reason: %s. " +
                        "If this wasn't you, please freeze your card immediately.",
                event.getAmount(), event.getCurrency(), event.getRiskScore(),
                String.join("; ", event.getTriggeredRules())
        );

        notificationService.sendNotification(event.getSourceAccountId(), "SMS", "CRITICAL", title, body);
        notificationService.sendNotification("fraud-operations@bank.com", "FRAUD_OPS", "CRITICAL",
                "FRAUD OPS ESCALATION: Tx " + event.getTransactionId(), body);
    }

    @KafkaListener(topics = "transaction-events", groupId = "notification-service-group")
    public void onTransferCompleted(Object event) {
        if (event instanceof TransferCompletedEvent completed) {
            String title = "✅ Transfer Completed";
            String body = String.format(
                    "Your transfer of %s %s to account %s was completed successfully in %d ms.",
                    completed.getAmount(), completed.getCurrency(), completed.getTargetAccountId(),
                    completed.getTotalProcessingTimeMs()
            );
            notificationService.sendNotification(completed.getSourceAccountId(), "SMS", "NORMAL", title, body);

            String creditBody = String.format(
                    "You received %s %s from account %s.",
                    completed.getAmount(), completed.getCurrency(), completed.getSourceAccountId()
            );
            notificationService.sendNotification(completed.getTargetAccountId(), "EMAIL", "NORMAL", "Funds Received", creditBody);
        } else if (event instanceof TransferFailedEvent failed) {
            String title = failed.isCompensated() ? "ℹ️ Transfer Failed - Funds Refunded" : "❌ Transfer Failed";
            String body = String.format(
                    "Transfer of %s %s to account %s failed (%s). %s",
                    failed.getAmount(), failed.getCurrency(), failed.getTargetAccountId(),
                    failed.getFailureReason(),
                    failed.isCompensated() ? "Your funds have been refunded to your account via SAGA compensation." : "Please contact support."
            );
            notificationService.sendNotification(failed.getSourceAccountId(), "SMS", "HIGH", title, body);
        }
    }

    @KafkaListener(topics = "account-events", groupId = "notification-service-group")
    public void onCompensationEvent(Object event) {
        if (event instanceof DebitCompensationEvent comp) {
            String title = "🔄 Balance Restored (Saga Compensation)";
            String body = String.format(
                    "Your account has been credited %s %s due to transaction rollback: %s",
                    comp.getAmount(), comp.getCurrency(), comp.getReason()
            );
            notificationService.sendNotification(comp.getAccountId(), "IN_APP", "HIGH", title, body);
        }
    }
}
