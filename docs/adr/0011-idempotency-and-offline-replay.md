# ADR 0011: Idempotency keys and the offline order replay contract

**Status:** Accepted
**Date:** 2026-09-05

## Context

POS terminals lose connectivity. A restaurant cannot stop taking orders because the
internet is down, and a network timeout must never produce a duplicate order or a double
charge.

## Decision

**Idempotency**

- Every mutating public endpoint accepts an `Idempotency-Key` header.
- The key, a request fingerprint and the serialised response are stored. A replay with
  the same key returns the stored response; a replay with the same key and a *different*
  fingerprint is rejected with 422.
- Redis provides the fast path; PostgreSQL is the durable record with a 7-day TTL.

**Offline order replay**

- Orders are created client-side with a UUIDv7 `client_order_id` and queued in IndexedDB.
- On reconnect, a batch endpoint replays the queue. The server deduplicates on a unique
  constraint over `(tenant_id, client_order_id)`.
- Menu and price data are cached locally with a version stamp. A terminal whose cache
  version is stale is forced to refresh before it may submit.

**Conflict policy.** If stock is insufficient at replay time, the order is **accepted**,
a negative stock adjustment is recorded, and an alert is raised. Rejecting a sale that
physically already happened would put the system out of step with reality. This mirrors
how production POS systems behave and is a deliberate, documented choice.

**Payment boundary.** Cash sales sync offline. Card and QR payments require connectivity
and are never taken offline. The boundary is explicit in the UI, not implicit.

## Consequences

- Negative stock is a legal state in the data model and must be handled everywhere stock
  is displayed or aggregated.
- The idempotency store is on the hot path and its latency budget is part of the order
  creation SLO.
- Clock skew on terminals is tolerated: UUIDv7 ordering is advisory, server receipt time
  is authoritative.
