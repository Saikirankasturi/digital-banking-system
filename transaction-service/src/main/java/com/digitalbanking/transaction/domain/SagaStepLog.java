package com.digitalbanking.transaction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "saga_step_logs", indexes = {
        @Index(name = "idx_step_saga_id", columnList = "saga_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Column(name = "step_name", nullable = false, length = 50)
    private String stepName;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // SUCCESS, FAILED, COMPENSATED

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "duration_ms")
    private long durationMs;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;
}
