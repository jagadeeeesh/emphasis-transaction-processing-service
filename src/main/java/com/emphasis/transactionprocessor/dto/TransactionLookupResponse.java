package com.emphasis.transactionprocessor.dto;

import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import com.emphasis.transactionprocessor.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionLookupResponse {

    private String transactionId;
    private String requestId;
    private String accountId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private int sequenceNumber;
    private TransactionStatus status;
    private int retryCount;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;

    public static TransactionLookupResponse from(TransactionRecord record) {
        TransactionLookupResponse response = new TransactionLookupResponse();
        response.transactionId = record.getTransactionId();
        response.requestId = record.getRequestId();
        response.accountId = record.getAccountId();
        response.transactionType = record.getTransactionType();
        response.amount = record.getAmount();
        response.currency = record.getCurrency();
        response.sequenceNumber = record.getSequenceNumber();
        response.status = record.getStatus();
        response.retryCount = record.getRetryCount();
        response.errorMessage = record.getErrorMessage();
        response.createdAt = record.getCreatedAt();
        response.updatedAt = record.getUpdatedAt();
        response.processedAt = record.getProcessedAt();
        return response;
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

    public TransactionStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
