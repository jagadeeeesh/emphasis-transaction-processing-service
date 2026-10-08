package com.emphasis.transactionprocessor.dto;

import com.emphasis.transactionprocessor.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionRequest {

    private String transactionId;
    private String requestId;
    private String accountId;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private Integer sequenceNumber;

    public TransactionRequest() {
    }

    public TransactionRequest(
            String transactionId,
            String requestId,
            String accountId,
            String transactionType,
            BigDecimal amount,
            String currency,
            Integer sequenceNumber
    ) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sequenceNumber = sequenceNumber;
    }

    public TransactionType parsedType() {
        if (transactionType == null || transactionType.isBlank()) {
            throw new IllegalArgumentException("transactionType is required");
        }
        try {
            return TransactionType.valueOf(transactionType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("transactionType must be CREDIT, DEBIT, or REVERSAL");
        }
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

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
