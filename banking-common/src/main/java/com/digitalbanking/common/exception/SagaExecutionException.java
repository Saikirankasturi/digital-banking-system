package com.digitalbanking.common.exception;

public class SagaExecutionException extends BankingException {
    private final String sagaId;
    private final String failedStep;

    public SagaExecutionException(String sagaId, String failedStep, String message, Throwable cause) {
        super("SAGA_EXECUTION_FAILED", String.format("Saga %s failed at step '%s': %s", sagaId, failedStep, message), cause);
        this.sagaId = sagaId;
        this.failedStep = failedStep;
    }

    public SagaExecutionException(String sagaId, String failedStep, String message) {
        super("SAGA_EXECUTION_FAILED", String.format("Saga %s failed at step '%s': %s", sagaId, failedStep, message));
        this.sagaId = sagaId;
        this.failedStep = failedStep;
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getFailedStep() {
        return failedStep;
    }
}
