package com.emphasis.transactionprocessor.repository;

import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.model.TransactionStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for transaction records. The in-memory implementation can be
 * replaced with a database-backed implementation without changing business logic.
 */
public interface TransactionRepository {

    /**
     * Atomically reserve a business transaction identity.
     * Returns empty if this thread/request established ownership,
     * or the existing record if the transactionId was already reserved.
     */
    Optional<TransactionRecord> putIfAbsent(TransactionRecord record);

    Optional<TransactionRecord> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    Optional<TransactionRecord> findByRequestId(String requestId);

    TransactionRecord save(TransactionRecord record);

    List<TransactionRecord> findPendingTransactions();

    List<TransactionRecord> findPendingByAccountId(String accountId);

    List<TransactionRecord> findAll();

    List<TransactionRecord> findByStatus(TransactionStatus status);
}
