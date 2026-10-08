package com.emphasis.transactionprocessor.controller;

import com.emphasis.transactionprocessor.dto.BatchProcessingResponse;
import com.emphasis.transactionprocessor.dto.ProcessingSummary;
import com.emphasis.transactionprocessor.dto.TransactionLookupResponse;
import com.emphasis.transactionprocessor.dto.TransactionRequest;
import com.emphasis.transactionprocessor.dto.TransactionResult;
import com.emphasis.transactionprocessor.service.TransactionProcessingService;
import com.emphasis.transactionprocessor.service.TransactionSummaryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionProcessingService processingService;
    private final TransactionSummaryService summaryService;

    public TransactionController(
            TransactionProcessingService processingService,
            TransactionSummaryService summaryService
    ) {
        this.processingService = processingService;
        this.summaryService = summaryService;
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchProcessingResponse> processBatch(@RequestBody JsonNode body) {
        return ResponseEntity.ok(processingService.processBatch(body));
    }

    @PostMapping
    public ResponseEntity<TransactionResult> processSingle(@RequestBody TransactionRequest request) {
        return ResponseEntity.ok(processingService.processSingle(request));
    }

    @GetMapping("/summary")
    public ResponseEntity<ProcessingSummary> summary() {
        return ResponseEntity.ok(summaryService.global());
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionLookupResponse> lookup(@PathVariable String transactionId) {
        return ResponseEntity.ok(processingService.lookup(transactionId));
    }
}
