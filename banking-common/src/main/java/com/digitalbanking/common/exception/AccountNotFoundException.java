package com.digitalbanking.common.exception;

public class AccountNotFoundException extends BankingException {
    public AccountNotFoundException(String accountId) {
        super("ACCOUNT_NOT_FOUND", "Bank account not found: " + accountId);
    }
}
