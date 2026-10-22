package com.digitalbanking.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {
    private String id;
    private String recipient;
    private String channel; // SMS, EMAIL, IN_APP, FRAUD_OPS
    private String priority; // HIGH, NORMAL, CRITICAL
    private String title;
    private String message;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
