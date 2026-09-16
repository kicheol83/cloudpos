# ADR 0009: Transactional outbox and Kafka topic design

**Status:** Accepted
**Date:** 2026-09-05

## Context

Services must publish events without dual-write inconsistency: a database commit followed
by a broker publish can lose events when the process dies in between.

## Decision

- **Transactional outbox** in every service. The domain write and the outbox row commit
  in one local transaction.
- **Debezium** reads the WAL and publishes to Kafka. A polling publisher is the
  documented fallback if Debezium proves too heavy operationally; the trade-off is
  latency and database load versus one more moving part.
- **Topic keying by `store_id`**, so that per-store event ordering is preserved. Ordering
  across stores is not required and is not guaranteed.
- **Consumers are idempotent** by `event_id`, deduplicated in Redis with a PostgreSQL
  table as the backstop when the Redis entry has expired.
- Schemas are versioned; consumers ignore unknown fields. Breaking changes are published
  as a new topic version, never as an in-place change.

## Consequences

- Publish latency is WAL-read latency, typically sub-second, not zero. Anything requiring
  synchronous consistency must not use events (see ADR 0010).
- Partition count must be sized for 10,000 stores with headroom; repartitioning later
  breaks key-based ordering.
- Debezium adds Kafka Connect to the operational surface.

## Alternatives considered

- **Publish directly from application code:** the dual-write problem this ADR exists to
  solve.
- **Choreographed events for all cross-service flows:** rejected for the money paths;
  see ADR 0010.
