package com.emphasis.transactionprocessor.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProcessingSummary {

    private int total;
    private int received;
    private int processing;
    private int processed;
    private int duplicate;
    private int pending;
    private int retryPending;
    private int failed;
    private Map<String, BigDecimal> accountBalances = new LinkedHashMap<>();
    private List<String> missingSequences = new ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getReceived() {
        return received;
    }

    public void setReceived(int received) {
        this.received = received;
    }

    public int getProcessing() {
        return processing;
    }

    public void setProcessing(int processing) {
        this.processing = processing;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getDuplicate() {
        return duplicate;
    }

    public void setDuplicate(int duplicate) {
        this.duplicate = duplicate;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getRetryPending() {
        return retryPending;
    }

    public void setRetryPending(int retryPending) {
        this.retryPending = retryPending;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public Map<String, BigDecimal> getAccountBalances() {
        return accountBalances;
    }

    public void setAccountBalances(Map<String, BigDecimal> accountBalances) {
        this.accountBalances = accountBalances;
    }

    public List<String> getMissingSequences() {
        return missingSequences;
    }

    public void setMissingSequences(List<String> missingSequences) {
        this.missingSequences = missingSequences;
    }
}
