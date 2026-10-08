package com.emphasis.transactionprocessor.model;

/**
 * Transaction processing lifecycle.
 *
 * <pre>
 * RECEIVED
 *    |
 *    +--> DUPLICATE          (business identity already reserved)
 *    |
 *    +--> PENDING            (out-of-order: sequence gap for the account)
 *    |
 *    +--> FAILED             (validation failure before processing)
 *    |
 *    +--> PROCESSING
 *            |
 *            +--> PROCESSED
 *            |
 *            +--> RETRY_PENDING --> PROCESSING --> PROCESSED | FAILED
 * </pre>
 *
 * PENDING exists so a future sequence number can be stored without applying a
 * business effect until the account's expected sequence arrives.
 */
public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    DUPLICATE,
    FAILED,
    RETRY_PENDING,
    PENDING
}
