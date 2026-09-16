# ADR 0010: Orchestrated sagas for the money paths

**Status:** Accepted
**Date:** 2026-09-05

## Context

Two flows cross service boundaries while a human waits at a terminal and money is
involved: order → payment → invoice, and cancel → refund → stock restore. When these
fail halfway, staff need an explanation and support needs a queryable state.

Choreographed events distribute this logic across four services, so reconstructing "why
is this order stuck" becomes archaeology across four log streams.

## Decision

- These two flows are **orchestrated**. The orchestrator lives in `order` and drives
  `payment`, `inventory` and `billing` through commands.
- Orchestration state is persisted in a `saga_instance` table: current step, attempt
  count, last error, compensation status. A stuck saga is a database query.
- Every step is idempotent and has an explicit compensation. Compensations are
  business-level reversals (refund, credit note, stock restore), never rollbacks.
- A sweeper job re-drives sagas that have been in a non-terminal state past their timeout,
  with exponential backoff and a dead-letter state requiring human action.
- All other cross-service flows remain choreographed via events.

**Consistency boundary, stated explicitly:** money and stock are never eventually
consistent *within a single business operation*. Everything else may be.

## Consequences

- `order` gains coordination responsibility, which is a coupling cost accepted knowingly.
- The `saga_instance` table becomes a first-class operational surface with a support UI.
- Compensation paths need the same test coverage as happy paths, including the case where
  a compensation itself fails.
