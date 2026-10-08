package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.model.TransactionType;
import com.emphasis.transactionprocessor.repository.AccountBalanceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Simulated business effect. CREDIT adds, DEBIT subtracts.
 * REVERSAL is defined as reversing a CREDIT of the same amount (balance decreases).
 * Negative balances are allowed in this lightweight simulation.
 */
@Service
public class AccountLedgerService {

    private final AccountBalanceRepository accountBalanceRepository;

    public AccountLedgerService(AccountBalanceRepository accountBalanceRepository) {
        this.accountBalanceRepository = accountBalanceRepository;
    }

    public BigDecimal apply(String accountId, TransactionType type, BigDecimal amount, String currency) {
        BigDecimal delta = switch (type) {
            case CREDIT -> amount;
            case DEBIT, REVERSAL -> amount.negate();
        };
        return accountBalanceRepository.applyDelta(accountId, delta, currency).getBalance();
    }

    public Map<String, BigDecimal> snapshot() {
        return accountBalanceRepository.findAllBalances();
    }
}
