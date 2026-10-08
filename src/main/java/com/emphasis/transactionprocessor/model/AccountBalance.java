package com.emphasis.transactionprocessor.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class AccountBalance {

    private final String accountId;
    private final BigDecimal balance;
    private final String currency;

    public AccountBalance(String accountId, BigDecimal balance, String currency) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountBalance withBalance(BigDecimal newBalance) {
        return new AccountBalance(accountId, newBalance, currency);
    }
}
