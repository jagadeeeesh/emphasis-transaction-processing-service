package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.config.ProcessingProperties;
import com.emphasis.transactionprocessor.dto.BatchProcessingResponse;
import com.emphasis.transactionprocessor.dto.TransactionLookupResponse;
import com.emphasis.transactionprocessor.dto.TransactionRequest;
import com.emphasis.transactionprocessor.dto.TransactionResult;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import com.emphasis.transactionprocessor.repository.InMemoryAccountBalanceRepository;
import com.emphasis.transactionprocessor.repository.InMemoryTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionProcessingServiceTest {

    private InMemoryTransactionRepository transactionRepository;
    private InMemoryAccountBalanceRepository balanceRepository;
    private TransactionProcessingService service;
    private ProcessingProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new ProcessingProperties();
        properties.getFailureSimulation().setFailFirstAttemptFor(List.of("TXN-2005"));
        transactionRepository = new InMemoryTransactionRepository();
        balanceRepository = new InMemoryAccountBalanceRepository();
        IdempotencyService idempotencyService = new IdempotencyService(transactionRepository);
        OrderingService orderingService = new OrderingService(properties);
        AccountLedgerService ledgerService = new AccountLedgerService(balanceRepository);
        TransactionSummaryService summaryService = new TransactionSummaryService(
                transactionRepository, ledgerService, idempotencyService, orderingService);
        objectMapper = new ObjectMapper();
        service = new TransactionProcessingService(
                transactionRepository,
                new TransactionValidator(),
                idempotencyService,
                orderingService,
                ledgerService,
                new ConfigurableFailureSimulator(properties),
                summaryService,
                properties,
                objectMapper
        );
    }

    @Test
    void validCreditUpdatesBalanceOnce() {
        TransactionResult result = service.processSingle(request("TXN-C1", "REQ-C1", "ACC-C", "CREDIT", "100", 1));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(balance("ACC-C")).isEqualByComparingTo("100");
    }

    @Test
    void validDebitDecreasesBalance() {
        service.processSingle(request("TXN-D1", "REQ-D1", "ACC-D", "CREDIT", "100", 1));
        TransactionResult result = service.processSingle(request("TXN-D2", "REQ-D2", "ACC-D", "DEBIT", "40", 2));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(balance("ACC-D")).isEqualByComparingTo("60");
    }

    @Test
    void reversalSubtractsAmountLikeCreditReversal() {
        service.processSingle(request("TXN-R1", "REQ-R1", "ACC-R", "CREDIT", "80", 1));
        TransactionResult result = service.processSingle(request("TXN-R2", "REQ-R2", "ACC-R", "REVERSAL", "80", 2));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(balance("ACC-R")).isEqualByComparingTo("0");
    }

    @Test
    void duplicateTransactionDoesNotUpdateBalanceTwice() {
        service.processSingle(request("TXN-DUP", "REQ-1", "ACC-DUP", "CREDIT", "100", 1));
        TransactionResult duplicate = service.processSingle(request("TXN-DUP", "REQ-1", "ACC-DUP", "CREDIT", "100", 1));

        assertThat(duplicate.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(balance("ACC-DUP")).isEqualByComparingTo("100");
    }

    @Test
    void sameTransactionIdDifferentRequestIdIsStillDuplicate() {
        service.processSingle(request("TXN-100", "REQ-1", "ACC-100", "CREDIT", "50", 1));
        TransactionResult duplicate = service.processSingle(request("TXN-100", "REQ-2", "ACC-100", "CREDIT", "50", 1));

        assertThat(duplicate.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(balance("ACC-100")).isEqualByComparingTo("50");
    }

    @Test
    void outOfOrderTransactionIsPendingUntilGapFills() {
        TransactionResult future = service.processSingle(request("TXN-2002", "REQ-2", "ACC-201", "DEBIT", "40", 2));
        assertThat(future.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(balanceRepository.findByAccountId("ACC-201")).isEmpty();

        TransactionResult first = service.processSingle(request("TXN-2001", "REQ-1", "ACC-201", "CREDIT", "100", 1));
        assertThat(first.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(transactionRepository.findByTransactionId("TXN-2002"))
                .hasValueSatisfying(record -> assertThat(record.getStatus()).isEqualTo(TransactionStatus.PROCESSED));
        assertThat(balance("ACC-201")).isEqualByComparingTo("60");
    }

    @Test
    void invalidNegativeAmountFails() {
        TransactionResult result = service.processSingle(request("TXN-2004", "REQ-4", "ACC-202", "DEBIT", "-10", 4));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("INVALID_AMOUNT");
        assertThat(balanceRepository.findByAccountId("ACC-202")).isEmpty();
    }

    @Test
    void missingRequiredFieldFails() {
        TransactionRequest request = request("TXN-MISS", "REQ-M", "ACC-M", "CREDIT", "10", 1);
        request.setAccountId(null);

        TransactionResult result = service.processSingle(request);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("MISSING_ACCOUNT_ID");
    }

    @Test
    void invalidRecordDoesNotStopBatch() {
        ArrayNode batch = objectMapper.createArrayNode();
        batch.add(objectMapper.valueToTree(request("TXN-B1", "REQ-B1", "ACC-B", "CREDIT", "10", 1)));
        batch.add(objectMapper.valueToTree(request("TXN-B2", "REQ-B2", "ACC-B", "DEBIT", "-5", 2)));
        batch.add(objectMapper.valueToTree(request("TXN-B3", "REQ-B3", "ACC-B", "CREDIT", "7", 2)));

        BatchProcessingResponse response = service.processBatch(batch);

        assertThat(response.getResults()).extracting(TransactionResult::getStatus)
                .containsExactly(TransactionStatus.PROCESSED, TransactionStatus.FAILED, TransactionStatus.PROCESSED);
        assertThat(balance("ACC-B")).isEqualByComparingTo("17");
        assertThat(response.getSummary().getProcessed()).isEqualTo(2);
        assertThat(response.getSummary().getFailed()).isEqualTo(1);
    }

    @Test
    void retryTransientFailureThenSucceedsWithoutDuplicateEffect() {
        TransactionResult result = service.processSingle(request("TXN-2005", "REQ-5", "ACC-RETRY", "CREDIT", "500", 1));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(balance("ACC-RETRY")).isEqualByComparingTo("500");
    }

    @Test
    void retryExhaustionResultsInFailedAndNoBusinessEffect() {
        properties.getFailureSimulation().getFailAlwaysFor().add("TXN-FAIL-ALL");

        TransactionResult result = service.processSingle(request("TXN-FAIL-ALL", "REQ-FA", "ACC-FA", "CREDIT", "10", 1));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getErrorMessage()).startsWith("RETRY_EXHAUSTED");
        assertThat(result.getRetryCount()).isEqualTo(3);
        assertThat(balanceRepository.findByAccountId("ACC-FA")).isEmpty();
    }

    @Test
    void retryDoesNotCreateDuplicateEffectOnLaterReplay() {
        service.processSingle(request("TXN-2005", "REQ-5", "ACC-RETRY2", "CREDIT", "500", 1));
        TransactionResult replay = service.processSingle(request("TXN-2005", "REQ-5B", "ACC-RETRY2", "CREDIT", "500", 1));

        assertThat(replay.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(balance("ACC-RETRY2")).isEqualByComparingTo("500");
    }

    @Test
    void processingSummaryCountsStatuses() {
        ArrayNode batch = objectMapper.createArrayNode();
        batch.add(objectMapper.valueToTree(request("TXN-2002", "REQ-2", "ACC-201", "DEBIT", "40", 2)));
        batch.add(objectMapper.valueToTree(request("TXN-2001", "REQ-1", "ACC-201", "CREDIT", "100", 1)));
        batch.add(objectMapper.valueToTree(request("TXN-2002", "REQ-2-DUP", "ACC-201", "DEBIT", "40", 2)));
        batch.add(objectMapper.valueToTree(request("TXN-2003", "REQ-3", "ACC-201", "CREDIT", "25", 3)));
        batch.add(objectMapper.valueToTree(request("TXN-2004", "REQ-4", "ACC-202", "DEBIT", "-10", 4)));
        batch.add(objectMapper.valueToTree(request("TXN-2005", "REQ-5", "ACC-203", "CREDIT", "500", 5)));

        BatchProcessingResponse response = service.processBatch(batch);

        assertThat(response.getResults()).extracting(TransactionResult::getTransactionId)
                .containsExactly("TXN-2002", "TXN-2001", "TXN-2002", "TXN-2003", "TXN-2004", "TXN-2005");
        assertThat(response.getResults()).extracting(TransactionResult::getStatus)
                .containsExactly(
                        TransactionStatus.PROCESSED,
                        TransactionStatus.PROCESSED,
                        TransactionStatus.DUPLICATE,
                        TransactionStatus.PROCESSED,
                        TransactionStatus.FAILED,
                        TransactionStatus.PENDING
                );
        assertThat(response.getSummary().getTotal()).isEqualTo(6);
        assertThat(response.getSummary().getProcessed()).isEqualTo(3);
        assertThat(response.getSummary().getDuplicate()).isEqualTo(1);
        assertThat(response.getSummary().getFailed()).isEqualTo(1);
        assertThat(response.getSummary().getPending()).isEqualTo(1);
        assertThat(balance("ACC-201")).isEqualByComparingTo("85");
    }

    @Test
    void transactionLookupReturnsStoredRecord() {
        service.processSingle(request("TXN-L1", "REQ-L1", "ACC-L", "CREDIT", "12.50", 1));

        TransactionLookupResponse lookup = service.lookup("TXN-L1");

        assertThat(lookup.getTransactionId()).isEqualTo("TXN-L1");
        assertThat(lookup.getRequestId()).isEqualTo("REQ-L1");
        assertThat(lookup.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(lookup.getSequenceNumber()).isEqualTo(1);
        assertThat(lookup.getCreatedAt()).isNotNull();
        assertThat(lookup.getProcessedAt()).isNotNull();
    }

    @Test
    void lookupUnknownTransactionThrows() {
        assertThatThrownBy(() -> service.lookup("MISSING"))
                .hasMessageContaining("MISSING");
    }

    @Test
    void failedSequenceDoesNotReleaseLaterPending() {
        properties.getFailureSimulation().getFailAlwaysFor().add("TXN-SEQ-FAIL");
        service.processSingle(request("TXN-SEQ-2", "REQ-S2", "ACC-SEQ", "CREDIT", "20", 2));
        TransactionResult failed = service.processSingle(request("TXN-SEQ-FAIL", "REQ-S1", "ACC-SEQ", "CREDIT", "10", 1));

        assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transactionRepository.findByTransactionId("TXN-SEQ-2"))
                .hasValueSatisfying(record -> assertThat(record.getStatus()).isEqualTo(TransactionStatus.PENDING));
        assertThat(balanceRepository.findByAccountId("ACC-SEQ")).isEmpty();
    }

    private TransactionRequest request(
            String transactionId,
            String requestId,
            String accountId,
            String type,
            String amount,
            int sequence
    ) {
        return new TransactionRequest(
                transactionId,
                requestId,
                accountId,
                type,
                new BigDecimal(amount),
                "USD",
                sequence
        );
    }

    private BigDecimal balance(String accountId) {
        return balanceRepository.findByAccountId(accountId).orElseThrow().getBalance();
    }
}
