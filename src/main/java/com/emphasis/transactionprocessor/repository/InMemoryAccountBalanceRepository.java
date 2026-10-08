package com.emphasis.transactionprocessor.repository;

import com.emphasis.transactionprocessor.exception.TransactionProcessingException;
import com.emphasis.transactionprocessor.model.AccountBalance;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryAccountBalanceRepository implements AccountBalanceRepository {

    private final ConcurrentMap<String, AccountBalance> balances = new ConcurrentHashMap<>();

    @Override
    public Optional<AccountBalance> findByAccountId(String accountId) {
        return Optional.ofNullable(balances.get(accountId));
    }

    @Override
    public AccountBalance applyDelta(String accountId, BigDecimal delta, String currency) {
        return balances.compute(accountId, (id, current) -> {
            if (current == null) {
                return new AccountBalance(id, delta, currency);
            }
            if (!current.getCurrency().equals(currency)) {
                throw new TransactionProcessingException("CURRENCY_MISMATCH");
            }
            return current.withBalance(current.getBalance().add(delta));
        });
    }

    @Override
    public Map<String, BigDecimal> findAllBalances() {
        return balances.values().stream()
                .collect(Collectors.toMap(
                        AccountBalance::getAccountId,
                        AccountBalance::getBalance,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }
}
