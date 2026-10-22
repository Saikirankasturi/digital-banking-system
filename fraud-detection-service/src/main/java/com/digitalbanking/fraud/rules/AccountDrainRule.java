package com.digitalbanking.fraud.rules;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class AccountDrainRule implements FraudRule {

    private static final BigDecimal DRAIN_PERCENTAGE_THRESHOLD = new BigDecimal("0.90"); // 90%
    private static final BigDecimal MIN_BALANCE_FOR_DRAIN_CHECK = new BigDecimal("1000.00");

    @Override
    public String getName() {
        return "ACCOUNT_DRAIN_RULE";
    }

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public RuleResult evaluate(FraudEvaluationRequest request) {
        BigDecimal balance = request.getCurrentBalance();
        BigDecimal amount = request.getAmount();

        if (balance == null || amount == null || balance.compareTo(MIN_BALANCE_FOR_DRAIN_CHECK) < 0) {
            return RuleResult.clean(getName());
        }

        BigDecimal drainRatio = amount.divide(balance, 4, RoundingMode.HALF_UP);
        if (drainRatio.compareTo(DRAIN_PERCENTAGE_THRESHOLD) >= 0) {
            return RuleResult.triggered(
                    getName(),
                    40,
                    String.format("Transfer amount (%s) represents %.1f%% of total account balance (%s) - suspicious drain pattern",
                            amount, drainRatio.multiply(new BigDecimal(100)).doubleValue(), balance)
            );
        }

        return RuleResult.clean(getName());
    }
}
