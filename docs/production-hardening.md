# Production Hardening

This document describes how the current lightweight service would evolve into a production system. These items are **not implemented**.

## Database

Replace in-memory maps with PostgreSQL or MySQL.

- `transactions.transaction_id` unique primary key — this is durable idempotency
- Optional `idempotency_keys(request_id)` unique table if clients retry by request identity and need the original HTTP response stored
- `accounts(account_id, balance, currency, expected_sequence, version)`
- Update balance, expected sequence, and transaction status in **one database transaction**
- Optimistic locking (`version`) on the account row, or `SELECT ... FOR UPDATE` when contention is per account
- `putIfAbsent` becomes `INSERT ... ON CONFLICT DO NOTHING` returning whether the row was created

A unique constraint is the production equivalent of the in-memory reservation. Application-level `containsKey` checks are not sufficient across instances.

## Messaging

Typical sources: Kafka, SQS, or RabbitMQ.

- Assume **at-least-once** delivery
- Consumer groups provide competing consumers
- Duplicate delivery is normal; idempotency must be in the processor, not the broker
- Partition or shard by `accountId` so sequences for one account are processed serially without a global lock
- Do not rely on broker ordering across partitions

## Retry

- Exponential backoff with jitter
- Cap attempts (`max-retries`)
- After exhaustion, send to a dead-letter queue
- Keep the original payload, last error, and attempt count
- Never apply the ledger on a retry unless the transaction row is still not `PROCESSED`
- Prefer transactional outbox if a downstream system must be notified exactly once

## Observability

- Metrics: processed, duplicate, pending, failed, retry, batch latency, gap age by account
- Structured JSON logs with `transactionId`, `requestId`, `accountId`, `status`, `reason`
- Tracing with a correlation id from the producer through the consumer
- Dashboards for failure rate and stuck `PENDING` sequences
- Alerts on retry exhaustion, poison messages, and accounts whose expected sequence has not moved

## Deployment

- Container image (Docker)
- Kubernetes / EKS: multiple replicas behind a service
- Probes: `GET /actuator/health` (liveness) plus a readiness check that the database is reachable
- Graceful shutdown: stop consuming, finish in-flight account locks, then exit
- Horizontal Pod Autoscaler on consumer lag, not only CPU

Health is already exposed in this assessment build. The rest of the deployment topology is out of scope.

## Security

- Authentication at the edge (OIDC / mTLS between services)
- Authorization by producer role; restrict replay APIs
- Secrets in a manager (Kubernetes secrets, AWS Secrets Manager); never in git
- Keep the existing input validation
- Encrypt data at rest and in transit
- Minimize PII in logs; do not log full customer payloads
- Audit trail for status transitions

## Scalability

- Horizontal consumers
- Partition by `accountId` so ordering remains local
- Database connection pooling
- Avoid JVM-wide locks; use row locks or partition affinity
- Bound pending buffers per account and alert on growth

The current `ConcurrentHashMap` plus per-account synchronized blocks will not scale across processes.

## Recovery

- Replay from DLQ after fixing the cause
- Rebuild balances from a ledger of `PROCESSED` transactions (reconciliation job)
- Gap scanner: accounts with `PENDING` older than N minutes
- Idempotent replay: inserting an already-`PROCESSED` `transactionId` is a no-op
- Backup and point-in-time recovery for the database

## Mapping from this assessment

| Assessment implementation | Production replacement |
| --- | --- |
| `ConcurrentHashMap.putIfAbsent` | Unique `transaction_id` insert |
| Per-account `synchronized` | Partitioning + row lock |
| Inline retry loop | Backoff worker + DLQ |
| HTTP batch | Queue consumer adapter |
| In-memory balances | Transactional account row |
| Log lines + summary JSON | Metrics, traces, dashboards |
