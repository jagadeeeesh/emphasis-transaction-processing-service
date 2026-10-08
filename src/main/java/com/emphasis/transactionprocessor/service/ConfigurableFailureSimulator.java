package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.config.ProcessingProperties;
import com.emphasis.transactionprocessor.dto.TransactionRequest;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableFailureSimulator implements FailureSimulator {

    private final ProcessingProperties properties;

    public ConfigurableFailureSimulator(ProcessingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean shouldFail(TransactionRequest request, int attempt) {
        if (request == null || request.getTransactionId() == null) {
            return false;
        }
        String transactionId = request.getTransactionId();
        ProcessingProperties.FailureSimulation simulation = properties.getFailureSimulation();
        if (simulation.getFailAlwaysFor().contains(transactionId)) {
            return true;
        }
        return attempt == 1 && simulation.getFailFirstAttemptFor().contains(transactionId);
    }
}
