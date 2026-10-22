package com.digitalbanking.transaction.domain;

import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_tx_saga_id", columnList = "saga_id"),
        @Index(name = "idx_tx_source_acc", columnList = "source_account_id"),
        @Index(name = "idx_tx_target_acc", columnList = "target_account_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "source_account_id", nullable = false, length = 36)
    private String sourceAccountId;

    @Column(name = "target_account_id", nullable = false, length = 36)
    private String targetAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "fraud_score")
    @Builder.Default
    private int fraudScore = 0;

    @Column(name = "failure_step", length = 50)
    private String failureStep;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "otp_verified")
    @Builder.Default
    private boolean otpVerified = false;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "location", length = 100)
    private String location;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
