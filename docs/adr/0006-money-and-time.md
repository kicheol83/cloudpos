# ADR 0006: Money and time representation

**Status:** Accepted
**Date:** 2026-09-05

## Context

The system computes tax, issues legally binding invoices, and reconciles against a
payment provider's settlement file. Rounding errors and date-boundary errors are not
cosmetic here; they produce failed reconciliations and incorrect tax filings.

Restaurants trade past midnight. A store open until 03:00 books those sales to the
previous business day, not the calendar day.

## Decision

**Money**

- Stored as `BIGINT` minor units plus an ISO-4217 `currency` column. KRW has zero decimal
  places, so one unit is one won. Floating point is never used for money.
- A single `Money` value object owns all arithmetic. Raw `long` arithmetic on monetary
  values is forbidden and enforced by ArchUnit.
- Tax rounding rules are defined once, in `billing`, and applied nowhere else.

**Time**

- All timestamps are `timestamptz`, stored in UTC.
- Every store has an IANA timezone and a configurable **business-day cutoff** (default
  05:00 local).
- Every transactional row carries a denormalised `business_date DATE`, computed at write
  time from the store's timezone and cutoff.
- All reports, settlements and shift closures group by `business_date`. Grouping by
  `created_at::date` is forbidden.

## Consequences

- `business_date` is also the partition key (ADR 0007), so this decision and the
  partitioning strategy are coupled.
- Changing a store's cutoff requires a backfill; the operation is scripted and audited.
- Every daily figure in the system agrees with every other, because there is exactly one
  definition of "today".
