# ADR 0007: Table partitioning and data retention

**Status:** Accepted
**Date:** 2026-09-05

## Context

Planning estimates at 10,000 stores (150 orders/store/day, 4.2 items/order, 5 recipe
lines/dish):

| Table | Rows / year |
| --- | --- |
| `orders` | 550 M |
| `order_items` | 2.3 B |
| `payments` | 550 M |
| `stock_movements` | 11 B |

`stock_movements` at 11 B rows/year is not economically storable at full granularity, and
unpartitioned tables of this size make vacuum, index maintenance and deletion impractical.

## Decision

**Partitioning** — declarative `RANGE` partitioning on `business_date`, automated with
`pg_partman`:

| Table | Interval |
| --- | --- |
| `orders`, `order_items`, `payments` | monthly |
| `stock_movements` | weekly |
| `audit_log` | monthly |

The repository layer enforces that every query against a partitioned table carries both
`tenant_id` and a bounded date range, so partition pruning always applies.

**Retention**

- `stock_movements`: 90 days at full granularity. After 90 days, aggregate into
  `stock_daily_summary` (tenant, store, ingredient, business_date, opening, in, out,
  waste, closing) and drop the raw partition.
- `orders`, `payments`, invoices: 5 years hot, matching Korean bookkeeping retention,
  then exported to Parquet in object storage.
- Reservation customer PII: purged 1 year after the reservation date (ADR 0013 and PIPA
  obligations).

## Consequences

- Deletion is a partition drop, not a `DELETE` — no bloat, no long-running vacuum.
- Queries that omit a date range fail fast in tests rather than performing a full scan in
  production.
- The aggregation job is a correctness-critical batch: if it fails, raw partitions must
  not be dropped. The drop is gated on a successful summary checksum.

## Alternatives considered

- **No partitioning, rely on indexes:** fails on deletion and vacuum at this row count.
- **Partition by `tenant_id` hash:** distributes load but makes retention (a time-based
  concern) impossible to implement as a partition drop.
