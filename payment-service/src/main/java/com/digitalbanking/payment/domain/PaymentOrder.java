package com.digitalbanking.payment.domain;

import com.digitalbanking.common.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_orders", indexes = {
        @Index(name = "idx_gateway_order_id", columnList = "gateway_order_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "gateway_order_id", unique = true, nullable = false, length = 64)
    private String gatewayOrderId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private Currency currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // CREATED, PAID, FAILED, CANCELLED

    @Column(name = "gateway_payment_id", length = 64)
    private String gatewayPaymentId;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
