package com.emphasis.transactionprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "processing")
public class ProcessingProperties {

    /**
     * Maximum processing attempts for a retryable failure (including the first attempt).
     */
    private int maxRetries = 3;

    /**
     * First expected sequence number for each account.
     */
    private int initialSequence = 1;

    private FailureSimulation failureSimulation = new FailureSimulation();

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getInitialSequence() {
        return initialSequence;
    }

    public void setInitialSequence(int initialSequence) {
        this.initialSequence = initialSequence;
    }

    public FailureSimulation getFailureSimulation() {
        return failureSimulation;
    }

    public void setFailureSimulation(FailureSimulation failureSimulation) {
        this.failureSimulation = failureSimulation;
    }

    public static class FailureSimulation {
        private List<String> failFirstAttemptFor = new ArrayList<>();
        private List<String> failAlwaysFor = new ArrayList<>();

        public List<String> getFailFirstAttemptFor() {
            return failFirstAttemptFor;
        }

        public void setFailFirstAttemptFor(List<String> failFirstAttemptFor) {
            this.failFirstAttemptFor = failFirstAttemptFor == null ? new ArrayList<>() : failFirstAttemptFor;
        }

        public List<String> getFailAlwaysFor() {
            return failAlwaysFor;
        }

        public void setFailAlwaysFor(List<String> failAlwaysFor) {
            this.failAlwaysFor = failAlwaysFor == null ? new ArrayList<>() : failAlwaysFor;
        }
    }
}
