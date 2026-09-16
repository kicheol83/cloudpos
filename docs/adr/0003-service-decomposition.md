# ADR 0003: Service boundaries drawn by load shape and failure domain

**Status:** Accepted
**Date:** 2026-09-05

## Context

The system must be a microservice architecture. The common failure mode in solo projects
is splitting by domain noun — one service per entity — producing many shallow services,
chatty synchronous calls, and no defensible answer to "why is this separate?".

## Decision

Eight services, split by three criteria only: **load shape**, **failure domain**, and
**compliance scope**.

| Service | Split criterion |
| --- | --- |
| identity | Shared dependency; must survive failures elsewhere. Holds staff PII. |
| catalog | Read-heavy, near-static; natural cache tier. |
| order | Highest transactional write rate; latency-sensitive. |
| inventory | Contention centre; highest raw write volume. |
| payment | External I/O, independent failure domain, PCI scope boundary. |
| billing | Append-only, audited, batch-shaped. |
| realtime-gateway | Scales on connection count, not request rate. |
| reporting | Analytical reads; must never contend with the transactional path. |

**Deliberately not separate**, each with a documented extraction trigger:

- **Table & Floor** → inside `order`. Table state changes only as a consequence of order
  and reservation events.
- **Reservation** → inside `order`. Shares the table-availability invariant. Two services
  owning one invariant is how double-bookings occur.
- **Kitchen** → a projection inside `order`, published via `realtime-gateway`.
- **Notification** → a consumer inside `realtime-gateway`.

Edge: one API gateway (Spring Cloud Gateway) for routing, JWT verification, per-tenant
rate limiting and request-ID propagation. Three BFFs behind it, one per frontend.

## Consequences

- Fewer services than a naive decomposition, each with a written justification.
- `order` is the largest service and will need internal module boundaries enforced by
  ArchUnit to avoid becoming a ball of mud.
- Extraction triggers must be measured, not assumed.

## Alternatives considered

Twelve-plus services split per entity: rejected. Every additional boundary buys a
network hop, a failure mode and a distributed transaction, and must pay for itself.
