package com.digitalbanking.common.exception;

public class DuplicateTransactionException extends BankingException {
    public DuplicateTransactionException(String idempotencyKey) {
        super("DUPLICATE_TRANSACTION", "A transaction with idempotency key '" + idempotencyKey + "' has already been processed or is currently in progress.");
    }
}
