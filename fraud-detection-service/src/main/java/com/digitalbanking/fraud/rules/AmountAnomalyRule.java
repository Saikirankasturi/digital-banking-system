package com.digitalbanking.fraud.rules;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AmountAnomalyRule implements FraudRule {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal EXTREME_AMOUNT_THRESHOLD = new BigDecimal("50000.00");

    @Override
    public String getName() {
        return "AMOUNT_ANOMALY_RULE";
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public RuleResult evaluate(FraudEvaluationRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount == null) {
            return RuleResult.clean(getName());
        }

        if (amount.compareTo(EXTREME_AMOUNT_THRESHOLD) >= 0) {
            return RuleResult.triggered(
                    getName(),
                    80,
                    String.format("Extreme transaction amount (%s %s) exceeds threshold of %s",
                            amount, request.getCurrency(), EXTREME_AMOUNT_THRESHOLD)
            );
        } else if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) >= 0) {
            return RuleResult.triggered(
                    getName(),
                    45,
                    String.format("High transaction amount (%s %s) exceeds standard threshold of %s - verification required",
                            amount, request.getCurrency(), HIGH_AMOUNT_THRESHOLD)
            );
        }

        return RuleResult.clean(getName());
    }
}
