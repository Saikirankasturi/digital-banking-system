package com.digitalbanking.fraud.rules;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BeneficiaryRiskRule implements FraudRule {

    // Simulated blacklist of mule accounts or sanction lists
    private static final Set<String> BLACKLISTED_ACCOUNTS = Set.of(
            "ACC-BLACK-9999",
            "MULE-ACCOUNT-666",
            "SANCTION-LIST-001"
    );

    private static final Set<String> HIGH_RISK_IPS = Set.of(
            "198.51.100.99",
            "203.0.113.55"
    );

    @Override
    public String getName() {
        return "BENEFICIARY_RISK_RULE";
    }

    @Override
    public int getOrder() {
        return 4;
    }

    @Override
    public RuleResult evaluate(FraudEvaluationRequest request) {
        String targetAccount = request.getTargetAccountId();
        if (targetAccount != null && BLACKLISTED_ACCOUNTS.contains(targetAccount)) {
            return RuleResult.triggered(
                    getName(),
                    100,
                    "Beneficiary account " + targetAccount + " is flagged on global sanction/anti-fraud blacklist"
            );
        }

        String ip = request.getIpAddress();
        if (ip != null && HIGH_RISK_IPS.contains(ip)) {
            return RuleResult.triggered(
                    getName(),
                    75,
                    "Originating IP " + ip + " belongs to known botnet/anonymizing proxy network"
            );
        }

        return RuleResult.clean(getName());
    }
}
