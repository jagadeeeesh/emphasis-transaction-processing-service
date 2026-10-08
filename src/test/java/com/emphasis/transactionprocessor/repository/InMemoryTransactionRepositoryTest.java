package com.emphasis.transactionprocessor.repository;

import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.model.TransactionStatus;
import com.emphasis.transactionprocessor.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTransactionRepositoryTest {

    @Test
    void putIfAbsentReservesOnlyOnceUnderContention() throws Exception {
        InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquired = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                start.await();
                TransactionRecord record = new TransactionRecord(
                        "TXN-2001",
                        "REQ-" + idx,
                        "ACC-201",
                        TransactionType.CREDIT,
                        new BigDecimal("100"),
                        "USD",
                        1,
                        3,
                        TransactionStatus.RECEIVED
                );
                Optional<TransactionRecord> existing = repository.putIfAbsent(record);
                if (existing.isEmpty()) {
                    acquired.incrementAndGet();
                }
                done.countDown();
                return null;
            });
        }

        start.countDown();
        done.await();
        pool.shutdownNow();

        assertThat(acquired.get()).isEqualTo(1);
        assertThat(repository.existsByTransactionId("TXN-2001")).isTrue();
    }
}
