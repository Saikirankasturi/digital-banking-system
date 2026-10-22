package com.digitalbanking.transaction.domain;

import com.digitalbanking.common.enums.SagaStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "saga_instances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {

    @Id
    @Column(name = "saga_id", nullable = false, updatable = false, length = 36)
    private String sagaId;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SagaStatus status;

    @Column(name = "current_step", length = 50)
    private String currentStep;

    @Column(name = "failure_step", length = 50)
    private String failureStep;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
