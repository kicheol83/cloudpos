# ADR 0015: ClickHouse as the reporting store

**Status:** Accepted
**Date:** 2026-09-05

## Context

Dashboards aggregate across billions of order-item rows: sales by hour, by dish, by
category, by store, by business date, with tenant-level and chain-level rollups. Running
these on the transactional PostgreSQL cluster would contend with order writes, which are
latency-sensitive.

## Decision

- A dedicated `reporting` service backed by **ClickHouse**, populated from Kafka events
  via the ClickHouse Kafka engine.
- Tables use `MergeTree` with `ORDER BY (tenant_id, store_id, business_date, ...)`,
  matching the tenancy and time model of ADR 0004 and 0006.
- Pre-aggregation via `AggregatingMergeTree` materialised views for the fixed dashboard
  queries; raw event rows retained for ad-hoc analysis.
- Reporting data is **explicitly eventually consistent**. The UI labels the freshness
  timestamp rather than pretending the numbers are live.
- The authoritative financial figures come from `billing`, not from ClickHouse. Reporting
  is for operational insight; settlement is for money.

## Consequences

- One more datastore to operate, back up and monitor. Accepted deliberately: the
  analytical access pattern is genuinely different and columnar storage is the right tool.
- Two sources of "total sales" exist. Which one is authoritative must be documented and
  visible in the UI, or it becomes a support problem.
- A reconciliation check compares ClickHouse daily totals against `billing` postings and
  alerts on divergence beyond a threshold.

## Alternatives considered

- **PostgreSQL read model with partitioning:** simpler, one fewer technology, adequate at
  100–1,000 stores. Rejected because it does not hold at the stated 10,000-store target,
  and because the analytical tier is a deliberate part of the project's scope.
- **TimescaleDB:** good for time-series, weaker for high-cardinality group-by across
  dish and category dimensions.
