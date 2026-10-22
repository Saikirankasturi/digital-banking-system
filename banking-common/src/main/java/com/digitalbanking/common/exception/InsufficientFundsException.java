package com.digitalbanking.common.exception;

public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountId, String currentBalance, String requestedAmount) {
        super("INSUFFICIENT_FUNDS",
                String.format("Account %s has insufficient funds. Available: %s, Requested: %s",
                        accountId, currentBalance, requestedAmount));
    }
}
