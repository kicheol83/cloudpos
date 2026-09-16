# ADR 0008: Concurrency control chosen per contention profile

**Status:** Accepted
**Date:** 2026-09-05

## Context

Four distinct concurrency problems exist, with different contention levels and different
correct user-facing behaviour. Applying one mechanism to all of them is wrong in at
least three of the four cases.

## Decision

| Case | Mechanism | Reason |
| --- | --- | --- |
| Ingredient stock deduction | `SELECT ... FOR UPDATE`, rows locked in ascending `ingredient_id` order across the whole order | High contention on popular ingredients; short transaction; fixed lock order prevents deadlock between concurrent orders sharing ingredients |
| Table state transition | Optimistic locking (`@Version`) | Low contention; a conflict is meaningful and should surface to the user as "another terminal changed this table" |
| Reservation slot booking | `EXCLUDE USING gist (tenant_id WITH =, table_id WITH =, period WITH &&)` via `btree_gist` | Lets the database enforce the no-double-booking invariant; application-level checks race |
| Invoice number allocation | Per-tenant counter row taken with `FOR UPDATE` inside the issuing transaction | Gapless numbering is a legal requirement; PostgreSQL sequences leak numbers on rollback (ADR 0013) |

**Stock write path.** The naive design — one insert per recipe line, synchronously —
produces ~4,000 writes/sec at peak. Instead: the full deduction for an order is computed
in memory, written as one JDBC batch insert, and balances are updated with one
`UPDATE ... SET qty = qty - ?` per ingredient in fixed ID order.

**`dish.can_be_served` is a projection**, recomputed asynchronously from `stock.changed`
events. It is allowed to be seconds stale and the UI treats it as advisory. The
authoritative availability check happens inside the order-confirmation transaction.

## Consequences

- Two different answers to "is this dish available?" exist by design: a fast advisory one
  and a slow authoritative one. This must be stated in the API contract.
- Lock ordering is a correctness invariant and is unit-tested, not assumed.
- Load tests publish measured throughput and deadlock counts for both the pessimistic and
  optimistic variants, as committed artefacts.
