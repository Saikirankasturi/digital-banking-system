package com.digitalbanking.fraud.engine;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import com.digitalbanking.common.dto.FraudEvaluationResult;
import com.digitalbanking.common.enums.FraudDecision;
import com.digitalbanking.fraud.rules.FraudRule;
import com.digitalbanking.fraud.rules.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudRuleEngine {

    private final List<FraudRule> rules;

    public FraudEvaluationResult evaluate(FraudEvaluationRequest request) {
        long startTime = System.currentTimeMillis();
        int totalScore = 0;
        List<String> triggeredReasons = new ArrayList<>();

        List<FraudRule> sortedRules = rules.stream()
                .sorted(Comparator.comparingInt(FraudRule::getOrder))
                .toList();

        for (FraudRule rule : sortedRules) {
            RuleResult result = rule.evaluate(request);
            if (result.isTriggered()) {
                totalScore += result.getScore();
                triggeredReasons.add(String.format("[%s: +%d] %s", rule.getName(), result.getScore(), result.getReason()));
            }
        }

        // Cap score at 100
        int finalScore = Math.min(100, totalScore);

        FraudDecision decision;
        if (finalScore >= 70) {
            decision = FraudDecision.REJECTED;
        } else if (finalScore >= 40) {
            decision = FraudDecision.REVIEW_FLAGGED;
        } else {
            decision = FraudDecision.APPROVED;
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Fraud evaluation completed for Tx {} in {}ms. Score: {}, Decision: {}",
                request.getTransactionId(), duration, finalScore, decision);

        return FraudEvaluationResult.builder()
                .transactionId(request.getTransactionId())
                .decision(decision)
                .riskScore(finalScore)
                .triggeredRules(triggeredReasons)
                .evaluationDurationMs(duration)
                .timestamp(Instant.now())
                .build();
    }
}
