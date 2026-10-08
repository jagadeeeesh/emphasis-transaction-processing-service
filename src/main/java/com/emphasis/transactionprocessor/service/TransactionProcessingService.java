package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.config.ProcessingProperties;
import com.emphasis.transactionprocessor.dto.BatchProcessingResponse;
import com.emphasis.transactionprocessor.dto.TransactionLookupResponse;
import com.emphasis.transactionprocessor.dto.TransactionRequest;
import com.emphasis.transactionprocessor.dto.TransactionResult;
import com.emphasis.transactionprocessor.exception.InvalidRequestException;
import com.emphasis.transactionprocessor.exception.InvalidTransactionException;
import com.emphasis.transactionprocessor.exception.RetryableProcessingException;
import com.emphasis.transactionprocessor.exception.TransactionNotFoundException;
import com.emphasis.transactionprocessor.exception.TransactionProcessingException;
import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import com.emphasis.transactionprocessor.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class TransactionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionValidator validator;
    private final IdempotencyService idempotencyService;
    private final OrderingService orderingService;
    private final AccountLedgerService accountLedgerService;
    private final FailureSimulator failureSimulator;
    private final TransactionSummaryService summaryService;
    private final ProcessingProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Object> accountLocks = new ConcurrentHashMap<>();

    public TransactionProcessingService(
            TransactionRepository transactionRepository,
            TransactionValidator validator,
            IdempotencyService idempotencyService,
            OrderingService orderingService,
            AccountLedgerService accountLedgerService,
            FailureSimulator failureSimulator,
            TransactionSummaryService summaryService,
            ProcessingProperties properties,
            ObjectMapper objectMapper
    ) {
        this.transactionRepository = transactionRepository;
        this.validator = validator;
        this.idempotencyService = idempotencyService;
        this.orderingService = orderingService;
        this.accountLedgerService = accountLedgerService;
        this.failureSimulator = failureSimulator;
        this.summaryService = summaryService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public BatchProcessingResponse processBatch(JsonNode body) {
        if (body == null || !body.isArray()) {
            throw new InvalidRequestException("Request body must be a JSON array of transactions");
        }
        List<TransactionResult> results = new ArrayList<>();
        for (JsonNode node : body) {
            TransactionRequest request;
            try {
                request = objectMapper.treeToValue(node, TransactionRequest.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                results.add(failedParseResult(node, "MALFORMED_RECORD", "Record could not be parsed"));
                continue;
            }
            results.add(processOne(request));
        }
        refreshNonDuplicateResults(results);
        return new BatchProcessingResponse(results, summaryService.fromResults(results));
    }

    public TransactionResult processSingle(TransactionRequest request) {
        TransactionResult result = processOne(request);
        if (result.getStatus() != TransactionStatus.DUPLICATE) {
            transactionRepository.findByTransactionId(result.getTransactionId())
                    .ifPresent(record -> {
                        result.setStatus(record.getStatus());
                        result.setRetryCount(record.getRetryCount());
                        result.setErrorMessage(record.getErrorMessage());
                    });
        }
        return result;
    }

    public TransactionLookupResponse lookup(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .map(TransactionLookupResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    TransactionResult processOne(TransactionRequest request) {
        try {
            validator.validate(request);
        } catch (InvalidTransactionException ex) {
            return handleInvalid(request, ex);
        }

        TransactionRecord incoming = TransactionRecord.from(request, TransactionStatus.RECEIVED, properties.getMaxRetries());
        IdempotencyService.Reservation reservation = idempotencyService.reserve(incoming);
        if (reservation.isDuplicate()) {
            log.info("transactionId={} requestId={} accountId={} status={} reason=DUPLICATE_TRANSACTION",
                    request.getTransactionId(), request.getRequestId(), request.getAccountId(), TransactionStatus.DUPLICATE);
            TransactionResult duplicate = TransactionResult.fromRecord(reservation.getRecord(), TransactionStatus.DUPLICATE);
            duplicate.setRequestId(request.getRequestId());
            return duplicate;
        }

        TransactionRecord record = reservation.getRecord();
        synchronized (lockFor(record.getAccountId())) {
            processOwnedRecord(record, request);
        }
        transactionRepository.save(record);
        return TransactionResult.fromRecord(record, record.getStatus());
    }

    private void processOwnedRecord(TransactionRecord record, TransactionRequest request) {
        if (orderingService.isFuture(record.getAccountId(), record.getSequenceNumber())) {
            List<Integer> missing = orderingService.missingSequences(record.getAccountId(), record.getSequenceNumber());
            String reason = "WAITING_FOR_SEQUENCE expected=" + orderingService.expectedSequence(record.getAccountId())
                    + " missing=" + missing;
            record.mark(TransactionStatus.PENDING, reason);
            log.info("transactionId={} requestId={} accountId={} status={} sequence={} {}",
                    record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                    record.getStatus(), record.getSequenceNumber(), reason);
            transactionRepository.save(record);
            return;
        }

        if (orderingService.isStale(record.getAccountId(), record.getSequenceNumber())) {
            record.mark(TransactionStatus.FAILED, "SEQUENCE_CONFLICT");
            log.warn("transactionId={} requestId={} accountId={} status={} reason=SEQUENCE_CONFLICT sequence={}",
                    record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                    record.getStatus(), record.getSequenceNumber());
            transactionRepository.save(record);
            return;
        }

        applyWithRetry(record, request);
        if (record.getStatus() == TransactionStatus.PROCESSED) {
            orderingService.advance(record.getAccountId(), record.getSequenceNumber());
            drainPending(record.getAccountId());
        }
    }

    private void applyWithRetry(TransactionRecord record, TransactionRequest request) {
        int maxAttempts = Math.max(1, properties.getMaxRetries());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            record.mark(TransactionStatus.PROCESSING, null);
            log.info("transactionId={} requestId={} accountId={} status={} attempt={}",
                    record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                    record.getStatus(), attempt);
            try {
                if (failureSimulator.shouldFail(request, attempt)) {
                    throw new RetryableProcessingException("SIMULATED_TRANSIENT_FAILURE");
                }
                accountLedgerService.apply(
                        record.getAccountId(),
                        record.getTransactionType(),
                        record.getAmount(),
                        record.getCurrency()
                );
                record.mark(TransactionStatus.PROCESSED, null);
                transactionRepository.save(record);
                log.info("transactionId={} requestId={} accountId={} status={} attempt={}",
                        record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                        record.getStatus(), attempt);
                return;
            } catch (TransactionProcessingException ex) {
                record.mark(TransactionStatus.FAILED, ex.getMessage());
                transactionRepository.save(record);
                log.warn("transactionId={} requestId={} accountId={} status={} attempt={} reason={}",
                        record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                        record.getStatus(), attempt, ex.getMessage());
                return;
            } catch (RetryableProcessingException ex) {
                record.incrementRetryCount();
                String reason = ex.getMessage();
                if (attempt >= maxAttempts) {
                    record.mark(TransactionStatus.FAILED, "RETRY_EXHAUSTED:" + reason);
                    transactionRepository.save(record);
                    log.warn("transactionId={} requestId={} accountId={} status={} attempt={} reason={}",
                            record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                            record.getStatus(), attempt, record.getErrorMessage());
                    return;
                }
                record.mark(TransactionStatus.RETRY_PENDING, reason);
                transactionRepository.save(record);
                log.info("transactionId={} requestId={} accountId={} status={} attempt={} reason={}",
                        record.getTransactionId(), record.getRequestId(), record.getAccountId(),
                        record.getStatus(), attempt, reason);
            }
        }
    }

    private void drainPending(String accountId) {
        List<TransactionRecord> pending = transactionRepository.findPendingByAccountId(accountId);
        for (TransactionRecord next : pending) {
            if (!orderingService.isExpected(accountId, next.getSequenceNumber())) {
                break;
            }
            TransactionRequest replay = new TransactionRequest(
                    next.getTransactionId(),
                    next.getRequestId(),
                    next.getAccountId(),
                    next.getTransactionType() == null ? null : next.getTransactionType().name(),
                    next.getAmount(),
                    next.getCurrency(),
                    next.getSequenceNumber()
            );
            applyWithRetry(next, replay);
            if (next.getStatus() != TransactionStatus.PROCESSED) {
                break;
            }
            orderingService.advance(accountId, next.getSequenceNumber());
        }
    }

    private TransactionResult handleInvalid(TransactionRequest request, InvalidTransactionException ex) {
        String transactionId = request.getTransactionId() == null || request.getTransactionId().isBlank()
                ? "UNKNOWN"
                : request.getTransactionId();
        TransactionRecord failed = TransactionRecord.from(request, TransactionStatus.FAILED, properties.getMaxRetries());
        failed.mark(TransactionStatus.FAILED, ex.getReasonCode());

        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
            IdempotencyService.Reservation reservation = idempotencyService.reserve(failed);
            if (reservation.isDuplicate()) {
                log.info("transactionId={} requestId={} status={} reason=DUPLICATE_TRANSACTION",
                        transactionId, request.getRequestId(), TransactionStatus.DUPLICATE);
                return TransactionResult.fromRecord(reservation.getRecord(), TransactionStatus.DUPLICATE);
            }
            transactionRepository.save(failed);
        }

        log.warn("transactionId={} requestId={} accountId={} status={} reason={}",
                transactionId, request.getRequestId(), request.getAccountId(),
                TransactionStatus.FAILED, ex.getReasonCode());
        return new TransactionResult(
                transactionId,
                request.getRequestId(),
                request.getAccountId(),
                TransactionStatus.FAILED,
                request.getSequenceNumber(),
                0,
                ex.getReasonCode()
        );
    }

    private TransactionResult failedParseResult(JsonNode node, String reason, String message) {
        String transactionId = textOrUnknown(node, "transactionId");
        String requestId = textOrUnknown(node, "requestId");
        String accountId = node != null && node.hasNonNull("accountId") ? node.get("accountId").asText() : null;
        log.warn("transactionId={} requestId={} status={} reason={} message={}",
                transactionId, requestId, TransactionStatus.FAILED, reason, message);
        return new TransactionResult(
                transactionId,
                requestId,
                accountId,
                TransactionStatus.FAILED,
                null,
                0,
                reason
        );
    }

    private void refreshNonDuplicateResults(List<TransactionResult> results) {
        for (TransactionResult result : results) {
            if (result.getStatus() == TransactionStatus.DUPLICATE || result.getTransactionId() == null) {
                continue;
            }
            transactionRepository.findByTransactionId(result.getTransactionId()).ifPresent(record -> {
                result.setStatus(record.getStatus());
                result.setRetryCount(record.getRetryCount());
                result.setErrorMessage(record.getErrorMessage());
            });
        }
    }

    private Object lockFor(String accountId) {
        return accountLocks.computeIfAbsent(accountId, key -> new Object());
    }

    private String textOrUnknown(JsonNode node, String field) {
        if (node != null && node.hasNonNull(field) && !node.get(field).asText().isBlank()) {
            return node.get(field).asText();
        }
        return "UNKNOWN";
    }
}
