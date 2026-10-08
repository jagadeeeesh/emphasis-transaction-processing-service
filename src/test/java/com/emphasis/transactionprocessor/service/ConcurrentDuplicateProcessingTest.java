package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.config.ProcessingProperties;
import com.emphasis.transactionprocessor.dto.TransactionRequest;
import com.emphasis.transactionprocessor.dto.TransactionResult;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import com.emphasis.transactionprocessor.repository.InMemoryAccountBalanceRepository;
import com.emphasis.transactionprocessor.repository.InMemoryTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentDuplicateProcessingTest {

    private InMemoryAccountBalanceRepository balanceRepository;
    private TransactionProcessingService service;

    @BeforeEach
    void setUp() {
        ProcessingProperties properties = new ProcessingProperties();
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        balanceRepository = new InMemoryAccountBalanceRepository();
        IdempotencyService idempotencyService = new IdempotencyService(transactionRepository);
        OrderingService orderingService = new OrderingService(properties);
        AccountLedgerService ledgerService = new AccountLedgerService(balanceRepository);
        TransactionSummaryService summaryService = new TransactionSummaryService(
                transactionRepository, ledgerService, idempotencyService, orderingService);
        service = new TransactionProcessingService(
                transactionRepository,
                new TransactionValidator(),
                idempotencyService,
                orderingService,
                ledgerService,
                new ConfigurableFailureSimulator(properties),
                summaryService,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void concurrentDuplicatesApplyBusinessEffectExactlyOnce() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int attempt = i;
            futures.add(pool.submit(() -> {
                start.await();
                TransactionRequest request = new TransactionRequest(
                        "TXN-2001",
                        "REQ-" + attempt,
                        "ACC-201",
                        "CREDIT",
                        new BigDecimal("100"),
                        "USD",
                        1
                );
                TransactionResult result = service.processSingle(request);
                if (result.getStatus() == TransactionStatus.PROCESSED) {
                    processed.incrementAndGet();
                } else if (result.getStatus() == TransactionStatus.DUPLICATE) {
                    duplicates.incrementAndGet();
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(processed.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(threads - 1);
        assertThat(balanceRepository.findByAccountId("ACC-201").orElseThrow().getBalance())
                .isEqualByComparingTo("100");
    }
}
