package com.digitalbanking.common.dto;

import com.digitalbanking.common.enums.FraudDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudEvaluationResult {
    private String transactionId;
    private FraudDecision decision;
    private int riskScore; // 0 to 100
    @Builder.Default
    private List<String> triggeredRules = new ArrayList<>();
    private long evaluationDurationMs;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
