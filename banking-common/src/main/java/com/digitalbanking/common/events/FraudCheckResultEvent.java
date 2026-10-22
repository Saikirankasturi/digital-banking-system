package com.digitalbanking.common.events;

import com.digitalbanking.common.enums.FraudDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResultEvent {
    private String transactionId;
    private String sagaId;
    private FraudDecision decision;
    private int riskScore;
    private List<String> triggeredRules;
    private long evaluationDurationMs;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
