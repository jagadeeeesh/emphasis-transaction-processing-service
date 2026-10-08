package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.model.TransactionRecord;
import com.emphasis.transactionprocessor.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reserves a business transaction identity exactly once.
 *
 * Ownership is established with {@code ConcurrentHashMap.putIfAbsent} through the
 * repository. Only the caller that inserts the record may apply a business effect.
 * A later request with the same transactionId — even with a different requestId —
 * is treated as a duplicate.
 *
 * In-memory synchronization protects a single JVM. A production multi-instance
 * deployment would require durable idempotency enforcement such as a database
 * unique constraint, transactional update, or equivalent distributed mechanism.
 */
@Service
public class IdempotencyService {

    private final TransactionRepository transactionRepository;
    private final AtomicInteger duplicateDetections = new AtomicInteger();

    public IdempotencyService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Reservation reserve(TransactionRecord incoming) {
        Optional<TransactionRecord> existing = transactionRepository.putIfAbsent(incoming);
        if (existing.isPresent()) {
            duplicateDetections.incrementAndGet();
            return Reservation.duplicate(existing.get());
        }
        return Reservation.acquired(incoming);
    }

    public int getDuplicateDetections() {
        return duplicateDetections.get();
    }

    public static final class Reservation {
        private final TransactionRecord record;
        private final boolean duplicate;

        private Reservation(TransactionRecord record, boolean duplicate) {
            this.record = record;
            this.duplicate = duplicate;
        }

        public static Reservation acquired(TransactionRecord record) {
            return new Reservation(record, false);
        }

        public static Reservation duplicate(TransactionRecord existing) {
            return new Reservation(existing, true);
        }

        public TransactionRecord getRecord() {
            return record;
        }

        public boolean isDuplicate() {
            return duplicate;
        }
    }
}
