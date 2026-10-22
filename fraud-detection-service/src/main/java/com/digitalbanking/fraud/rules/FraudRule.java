package com.digitalbanking.fraud.rules;

import com.digitalbanking.common.dto.FraudEvaluationRequest;

public interface FraudRule {
    String getName();
    RuleResult evaluate(FraudEvaluationRequest request);
    int getOrder();
}
