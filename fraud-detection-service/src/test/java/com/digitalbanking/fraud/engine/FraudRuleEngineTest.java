package com.digitalbanking.fraud.engine;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import com.digitalbanking.common.dto.FraudEvaluationResult;
import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.FraudDecision;
import com.digitalbanking.fraud.rules.AccountDrainRule;
import com.digitalbanking.fraud.rules.AmountAnomalyRule;
import com.digitalbanking.fraud.rules.BeneficiaryRiskRule;
import com.digitalbanking.fraud.rules.VelocityRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudRuleEngineTest {

    private FraudRuleEngine ruleEngine;
    private VelocityRule velocityRule;
    private AmountAnomalyRule amountRule;
    private AccountDrainRule drainRule;
    private BeneficiaryRiskRule beneficiaryRule;

    @BeforeEach
    void setUp() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        // Using in-memory fallback for VelocityRule in unit tests
        velocityRule = new VelocityRule(mockRedis);
        amountRule = new AmountAnomalyRule();
        drainRule = new AccountDrainRule();
        beneficiaryRule = new BeneficiaryRiskRule();

        ruleEngine = new FraudRuleEngine(List.of(velocityRule, amountRule, drainRule, beneficiaryRule));
    }

    @Test
    @DisplayName("Clean normal transaction: Approved with 0 risk score")
    void testCleanTransactionApproved() {
        FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId("ACC-101")
                .targetAccountId("ACC-202")
                .amount(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .currentBalance(new BigDecimal("5000.00"))
                .ipAddress("192.168.1.1")
                .build();

        FraudEvaluationResult result = ruleEngine.evaluate(req);

        assertEquals(FraudDecision.APPROVED, result.getDecision());
        assertEquals(0, result.getRiskScore());
        assertTrue(result.getTriggeredRules().isEmpty());
    }

    @Test
    @DisplayName("High amount anomaly: Flagged for OTP challenge")
    void testHighAmountFlagged() {
        FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId("ACC-101")
                .targetAccountId("ACC-202")
                .amount(new BigDecimal("15000.00"))
                .currency(Currency.USD)
                .currentBalance(new BigDecimal("50000.00"))
                .build();

        FraudEvaluationResult result = ruleEngine.evaluate(req);

        assertEquals(FraudDecision.REVIEW_FLAGGED, result.getDecision());
        assertTrue(result.getRiskScore() >= 40 && result.getRiskScore() < 70);
        assertFalse(result.getTriggeredRules().isEmpty());
    }

    @Test
    @DisplayName("Extreme amount anomaly: Blocked outright")
    void testExtremeAmountBlocked() {
        FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId("ACC-101")
                .targetAccountId("ACC-202")
                .amount(new BigDecimal("60000.00"))
                .currency(Currency.USD)
                .currentBalance(new BigDecimal("100000.00"))
                .build();

        FraudEvaluationResult result = ruleEngine.evaluate(req);

        assertEquals(FraudDecision.REJECTED, result.getDecision());
        assertTrue(result.getRiskScore() >= 70);
    }

    @Test
    @DisplayName("Blacklisted beneficiary account: Instant 100 risk score rejection")
    void testBlacklistedBeneficiaryRejected() {
        FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId("ACC-101")
                .targetAccountId("ACC-BLACK-9999") // on blacklist
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .currentBalance(new BigDecimal("5000.00"))
                .build();

        FraudEvaluationResult result = ruleEngine.evaluate(req);

        assertEquals(FraudDecision.REJECTED, result.getDecision());
        assertEquals(100, result.getRiskScore());
    }

    @Test
    @DisplayName("Rapid velocity draining: Triggers fraud flag after exceeding threshold")
    void testVelocityCheckDetection() {
        String sourceAccount = "ACC-VELOCITY-TEST";

        // Perform 4 rapid transactions
        for (int i = 0; i < 3; i++) {
            FraudEvaluationRequest req = FraudEvaluationRequest.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .sourceAccountId(sourceAccount)
                    .targetAccountId("ACC-RECEIVER")
                    .amount(new BigDecimal("50.00"))
                    .currency(Currency.USD)
                    .currentBalance(new BigDecimal("10000.00"))
                    .build();
            ruleEngine.evaluate(req);
        }

        // 4th transaction within sliding window
        FraudEvaluationRequest fourthTx = FraudEvaluationRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId(sourceAccount)
                .targetAccountId("ACC-RECEIVER")
                .amount(new BigDecimal("50.00"))
                .currency(Currency.USD)
                .currentBalance(new BigDecimal("10000.00"))
                .build();

        FraudEvaluationResult result = ruleEngine.evaluate(fourthTx);
        assertEquals(FraudDecision.REVIEW_FLAGGED, result.getDecision());
        assertTrue(result.getRiskScore() >= 50);
        assertTrue(result.getTriggeredRules().stream().anyMatch(r -> r.contains("VELOCITY")));
    }
}
