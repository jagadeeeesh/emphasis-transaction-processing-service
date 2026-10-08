package com.emphasis.transactionprocessor.exception;

public class InvalidTransactionException extends RuntimeException {

    private final String reasonCode;

    public InvalidTransactionException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
