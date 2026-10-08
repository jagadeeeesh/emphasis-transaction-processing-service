package com.emphasis.transactionprocessor.dto;

import com.emphasis.transactionprocessor.model.TransactionStatus;

public class TransactionResult {

    private String transactionId;
    private String requestId;
    private String accountId;
    private TransactionStatus status;
    private Integer sequenceNumber;
    private Integer retryCount;
    private String errorMessage;

    public TransactionResult() {
    }

    public TransactionResult(
            String transactionId,
            String requestId,
            String accountId,
            TransactionStatus status,
            Integer sequenceNumber,
            Integer retryCount,
            String errorMessage
    ) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.accountId = accountId;
        this.status = status;
        this.sequenceNumber = sequenceNumber;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
    }

    public static TransactionResult fromRecord(
            com.emphasis.transactionprocessor.model.TransactionRecord record,
            TransactionStatus overrideStatus
    ) {
        return new TransactionResult(
                record.getTransactionId(),
                record.getRequestId(),
                record.getAccountId(),
                overrideStatus,
                record.getSequenceNumber(),
                record.getRetryCount(),
                overrideStatus == TransactionStatus.DUPLICATE
                        ? "DUPLICATE_TRANSACTION"
                        : record.getErrorMessage()
        );
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
