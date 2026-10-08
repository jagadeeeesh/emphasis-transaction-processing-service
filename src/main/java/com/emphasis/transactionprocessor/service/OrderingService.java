package com.emphasis.transactionprocessor.service;

import com.emphasis.transactionprocessor.config.ProcessingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Sequence-based ordering scoped per accountId.
 * Expected sequence starts at {@code processing.initial-sequence} (default 1).
 * Future sequences are held as PENDING until the gap is filled.
 */
@Service
public class OrderingService {

    private final ProcessingProperties properties;
    private final ConcurrentMap<String, Integer> expectedSequenceByAccount = new ConcurrentSkipListMap<>();

    public OrderingService(ProcessingProperties properties) {
        this.properties = properties;
    }

    public int expectedSequence(String accountId) {
        return expectedSequenceByAccount.computeIfAbsent(accountId, ignored -> properties.getInitialSequence());
    }

    public boolean isExpected(String accountId, int sequenceNumber) {
        return sequenceNumber == expectedSequence(accountId);
    }

    public boolean isFuture(String accountId, int sequenceNumber) {
        return sequenceNumber > expectedSequence(accountId);
    }

    public boolean isStale(String accountId, int sequenceNumber) {
        return sequenceNumber < expectedSequence(accountId);
    }

    public void advance(String accountId, int processedSequence) {
        expectedSequenceByAccount.put(accountId, processedSequence + 1);
    }

    public List<Integer> missingSequences(String accountId, int receivedSequence) {
        int expected = expectedSequence(accountId);
        List<Integer> missing = new ArrayList<>();
        for (int sequence = expected; sequence < receivedSequence; sequence++) {
            missing.add(sequence);
        }
        return missing;
    }

    public List<String> describeGaps() {
        List<String> gaps = new ArrayList<>();
        expectedSequenceByAccount.forEach((accountId, expected) ->
                gaps.add("accountId=" + accountId + " nextExpectedSequence=" + expected));
        return gaps;
    }
}
