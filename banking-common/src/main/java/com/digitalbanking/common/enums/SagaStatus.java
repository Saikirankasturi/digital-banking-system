package com.digitalbanking.common.enums;

public enum SagaStatus {
    STARTED,
    FRAUD_CHECK_PASSED,
    SOURCE_DEBITED,
    BENEFICIARY_CREDITED,
    COMPLETED,
    COMPENSATION_TRIGGERED,
    COMPENSATED,
    FAILED
}
