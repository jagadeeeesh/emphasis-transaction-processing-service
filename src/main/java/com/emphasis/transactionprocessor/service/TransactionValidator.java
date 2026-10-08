package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.dto.TransactionRequest;
import com.emphasis.transactionprocessor.exception.InvalidTransactionException;
import com.emphasis.transactionprocessor.model.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class TransactionValidator {

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    public void validate(TransactionRequest request) {
        requireText(request.getTransactionId(), "MISSING_TRANSACTION_ID", "transactionId is required");
        requireText(request.getRequestId(), "MISSING_REQUEST_ID", "requestId is required");
        requireText(request.getAccountId(), "MISSING_ACCOUNT_ID", "accountId is required");
        requireText(request.getTransactionType(), "MISSING_TRANSACTION_TYPE", "transactionType is required");

        try {
            TransactionType.valueOf(request.getTransactionType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidTransactionException("INVALID_TRANSACTION_TYPE", "transactionType must be CREDIT, DEBIT, or REVERSAL");
        }

        if (request.getAmount() == null) {
            throw new InvalidTransactionException("MISSING_AMOUNT", "amount is required");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("INVALID_AMOUNT", "amount must be greater than 0");
        }

        requireText(request.getCurrency(), "MISSING_CURRENCY", "currency is required");
        String currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(currency).matches()) {
            throw new InvalidTransactionException("INVALID_CURRENCY", "currency must be a 3-character code");
        }
        request.setCurrency(currency);

        if (request.getSequenceNumber() == null) {
            throw new InvalidTransactionException("MISSING_SEQUENCE_NUMBER", "sequenceNumber is required");
        }
        if (request.getSequenceNumber() <= 0) {
            throw new InvalidTransactionException("INVALID_SEQUENCE_NUMBER", "sequenceNumber must be greater than 0");
        }
    }

    private void requireText(String value, String reason, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException(reason, message);
        }
    }
}
