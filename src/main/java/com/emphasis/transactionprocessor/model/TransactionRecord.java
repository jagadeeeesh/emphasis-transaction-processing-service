package com.emphasis.transactionprocessor.model;

import com.emphasis.transactionprocessor.dto.TransactionRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Canonical stored representation of a business transaction.
 * Identity is {@code transactionId}; {@code requestId} is the delivery attempt
 * that first reserved this identity.
 */
public class TransactionRecord {

    private final String transactionId;
    private final String requestId;
    private final String accountId;
    private final TransactionType transactionType;
    private final BigDecimal amount;
    private final String currency;
    private final int sequenceNumber;
    private final int maxRetries;

    private TransactionStatus status;
    private int retryCount;
    private String errorMessage;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;

    public TransactionRecord(
            String transactionId,
            String requestId,
            String accountId,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            int sequenceNumber,
            int maxRetries,
            TransactionStatus status
    ) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.requestId = requestId;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sequenceNumber = sequenceNumber;
        this.maxRetries = maxRetries;
        this.status = status;
        this.retryCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static TransactionRecord from(TransactionRequest request, TransactionStatus status, int maxRetries) {
        TransactionType type = null;
        try {
            type = request.parsedType();
        } catch (RuntimeException ignored) {
            // invalid type is represented as FAILED by the validator; type may be null here
        }
        int sequence = request.getSequenceNumber() == null ? 0 : request.getSequenceNumber();
        return new TransactionRecord(
                request.getTransactionId() == null ? "UNKNOWN" : request.getTransactionId(),
                request.getRequestId(),
                request.getAccountId(),
                type,
                request.getAmount(),
                request.getCurrency(),
                sequence,
                maxRetries,
                status
        );
    }

    public synchronized void mark(TransactionStatus newStatus, String error) {
        this.status = newStatus;
        this.errorMessage = error;
        this.updatedAt = Instant.now();
        if (newStatus == TransactionStatus.PROCESSED) {
            this.processedAt = this.updatedAt;
        }
    }

    public synchronized void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public synchronized TransactionStatus getStatus() {
        return status;
    }

    public synchronized int getRetryCount() {
        return retryCount;
    }

    public synchronized String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized Instant getUpdatedAt() {
        return updatedAt;
    }

    public synchronized Instant getProcessedAt() {
        return processedAt;
    }
}
