package com.emphasis.transactionprocessor.repository;

import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final ConcurrentMap<String, TransactionRecord> byTransactionId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> transactionIdByRequestId = new ConcurrentHashMap<>();

    @Override
    public Optional<TransactionRecord> putIfAbsent(TransactionRecord record) {
        TransactionRecord existing = byTransactionId.putIfAbsent(record.getTransactionId(), record);
        if (existing == null) {
            if (record.getRequestId() != null) {
                transactionIdByRequestId.putIfAbsent(record.getRequestId(), record.getTransactionId());
            }
            return Optional.empty();
        }
        return Optional.of(existing);
    }

    @Override
    public Optional<TransactionRecord> findByTransactionId(String transactionId) {
        return Optional.ofNullable(byTransactionId.get(transactionId));
    }

    @Override
    public boolean existsByTransactionId(String transactionId) {
        return byTransactionId.containsKey(transactionId);
    }

    @Override
    public Optional<TransactionRecord> findByRequestId(String requestId) {
        String transactionId = transactionIdByRequestId.get(requestId);
        if (transactionId == null) {
            return Optional.empty();
        }
        return findByTransactionId(transactionId);
    }

    @Override
    public TransactionRecord save(TransactionRecord record) {
        byTransactionId.put(record.getTransactionId(), record);
        if (record.getRequestId() != null) {
            transactionIdByRequestId.putIfAbsent(record.getRequestId(), record.getTransactionId());
        }
        return record;
    }

    @Override
    public List<TransactionRecord> findPendingTransactions() {
        return findByStatus(TransactionStatus.PENDING);
    }

    @Override
    public List<TransactionRecord> findPendingByAccountId(String accountId) {
        return byTransactionId.values().stream()
                .filter(record -> record.getStatus() == TransactionStatus.PENDING)
                .filter(record -> accountId.equals(record.getAccountId()))
                .sorted(Comparator.comparingInt(TransactionRecord::getSequenceNumber))
                .toList();
    }

    @Override
    public List<TransactionRecord> findAll() {
        return List.copyOf(byTransactionId.values());
    }

    @Override
    public List<TransactionRecord> findByStatus(TransactionStatus status) {
        return byTransactionId.values().stream()
                .filter(record -> record.getStatus() == status)
                .toList();
    }
}
