package com.emphasis.transactionprocessor.repository;

import com.emphasis.transactionprocessor.model.AccountBalance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public interface AccountBalanceRepository {

    Optional<AccountBalance> findByAccountId(String accountId);

    AccountBalance applyDelta(String accountId, BigDecimal delta, String currency);

    Map<String, BigDecimal> findAllBalances();
}
