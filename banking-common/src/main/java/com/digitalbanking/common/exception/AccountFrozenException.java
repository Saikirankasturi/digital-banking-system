package com.digitalbanking.common.exception;

public class AccountFrozenException extends BankingException {
    public AccountFrozenException(String accountId, String status) {
        super("ACCOUNT_INACTIVE", String.format("Account %s is not active (current status: %s)", accountId, status));
    }
}
