package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.dto.ProcessingSummary;
import com.emphasis.transactionprocessor.dto.TransactionResult;
import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import com.emphasis.transactionprocessor.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionSummaryService {

    private final TransactionRepository transactionRepository;
    private final AccountLedgerService accountLedgerService;
    private final IdempotencyService idempotencyService;
    private final OrderingService orderingService;

    public TransactionSummaryService(
            TransactionRepository transactionRepository,
            AccountLedgerService accountLedgerService,
            IdempotencyService idempotencyService,
            OrderingService orderingService
    ) {
        this.transactionRepository = transactionRepository;
        this.accountLedgerService = accountLedgerService;
        this.idempotencyService = idempotencyService;
        this.orderingService = orderingService;
    }

    public ProcessingSummary fromResults(List<TransactionResult> results) {
        ProcessingSummary summary = new ProcessingSummary();
        summary.setTotal(results.size());
        for (TransactionResult result : results) {
            increment(summary, result.getStatus());
        }
        summary.setAccountBalances(accountLedgerService.snapshot());
        summary.setMissingSequences(orderingService.describeGaps());
        return summary;
    }

    public ProcessingSummary global() {
        List<TransactionRecord> records = transactionRepository.findAll();
        ProcessingSummary summary = new ProcessingSummary();
        summary.setTotal(records.size());
        for (TransactionRecord record : records) {
            increment(summary, record.getStatus());
        }
        summary.setDuplicate(idempotencyService.getDuplicateDetections());
        summary.setAccountBalances(accountLedgerService.snapshot());
        summary.setMissingSequences(orderingService.describeGaps());
        return summary;
    }

    private void increment(ProcessingSummary summary, TransactionStatus status) {
        if (status == null) {
            return;
        }
        switch (status) {
            case RECEIVED -> summary.setReceived(summary.getReceived() + 1);
            case PROCESSING -> summary.setProcessing(summary.getProcessing() + 1);
            case PROCESSED -> summary.setProcessed(summary.getProcessed() + 1);
            case DUPLICATE -> summary.setDuplicate(summary.getDuplicate() + 1);
            case PENDING -> summary.setPending(summary.getPending() + 1);
            case RETRY_PENDING -> summary.setRetryPending(summary.getRetryPending() + 1);
            case FAILED -> summary.setFailed(summary.getFailed() + 1);
        }
    }
}
