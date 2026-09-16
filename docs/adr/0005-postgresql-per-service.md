# ADR 0005: PostgreSQL 17, one logical database per service

**Status:** Accepted
**Date:** 2026-09-05

## Context

Eight services need durable state. Sharing one schema across services would couple
deployments and make the service boundaries fictional.

## Decision

PostgreSQL 17. One logical database per service, with its own credentials and its own
Flyway migration history. No service reads another service's tables; data crosses
boundaries only through APIs or Kafka events.

Persistence: Spring Data JPA for aggregates, QueryDSL for dynamic filters, plain JDBC
batch for the high-volume write paths (`stock_movements`, order item inserts).

Connection management: PgBouncer in transaction pooling mode. **Note the JDBC
interaction** — server-side prepared statements must be disabled
(`prepareThreshold=0`) on transaction-pooled connections, or that pool must run in
session mode. This is a known trap and is configured explicitly, not discovered in
production.

Read replicas serve `reporting` ingestion and admin analytics. The transactional path
never reads from a replica: stale reads on order or table state are user-visible bugs.

## Consequences

- No cross-service joins. Reporting reconstructs the joined view from events.
- Referential integrity across service boundaries is enforced by sagas, not foreign keys.
- Operational cost of eight databases is accepted; they run on one cluster initially.

## Alternatives considered

- **One shared database with per-service schemas:** cheaper to operate, but makes the
  temptation to join across boundaries irresistible, which silently deletes the
  architecture.
- **MySQL:** no RLS, weaker partitioning, no `EXCLUDE` constraints, weaker JSON. RLS and
  exclusion constraints are both load-bearing here (ADR 0004, ADR 0008).
