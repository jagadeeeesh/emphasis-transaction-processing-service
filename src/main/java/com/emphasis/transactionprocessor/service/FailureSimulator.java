package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.dto.TransactionRequest;

public interface FailureSimulator {

    boolean shouldFail(TransactionRequest request, int attempt);
}
