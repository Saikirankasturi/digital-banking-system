package com.digitalbanking.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {
    private String ruleName;
    private boolean triggered;
    private int score;
    private String reason;

    public static RuleResult clean(String ruleName) {
        return RuleResult.builder()
                .ruleName(ruleName)
                .triggered(false)
                .score(0)
                .reason("Passed without anomaly")
                .build();
    }

    public static RuleResult triggered(String ruleName, int score, String reason) {
        return RuleResult.builder()
                .ruleName(ruleName)
                .triggered(true)
                .score(score)
                .reason(reason)
                .build();
    }
}
