package com.digitalbanking.common.exception;

public class FraudDetectedException extends BankingException {
    private final int riskScore;

    public FraudDetectedException(String message, int riskScore) {
        super("FRAUD_DETECTED", message);
        this.riskScore = riskScore;
    }

    public int getRiskScore() {
        return riskScore;
    }
}
