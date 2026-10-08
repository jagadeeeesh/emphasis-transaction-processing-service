package com.emphasis.transactionprocessor.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchProcessingResponse {

    private List<TransactionResult> results = new ArrayList<>();
    private ProcessingSummary summary = new ProcessingSummary();

    public BatchProcessingResponse() {
    }

    public BatchProcessingResponse(List<TransactionResult> results, ProcessingSummary summary) {
        this.results = results;
        this.summary = summary;
    }

    public List<TransactionResult> getResults() {
        return results;
    }

    public void setResults(List<TransactionResult> results) {
        this.results = results;
    }

    public ProcessingSummary getSummary() {
        return summary;
    }

    public void setSummary(ProcessingSummary summary) {
        this.summary = summary;
    }
}
