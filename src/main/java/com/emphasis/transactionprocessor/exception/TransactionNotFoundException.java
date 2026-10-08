package com.emphasis.transactionprocessor.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
