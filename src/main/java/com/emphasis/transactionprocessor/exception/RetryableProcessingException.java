package com.emphasis.transactionprocessor.exception;

public class RetryableProcessingException extends RuntimeException {

    public RetryableProcessingException(String message) {
        super(message);
    }
}
