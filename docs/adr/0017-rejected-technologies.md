# ADR 0017: Technologies deliberately not adopted

**Status:** Accepted
**Date:** 2026-09-05

## Context

Restraint is an architectural decision. A system's complexity budget is finite, and every
technology added must justify its operational cost. Recording what was rejected — and the
condition that would change the answer — is as informative as recording what was chosen.

## Decision

| Technology | Rejected because | Would reconsider if |
| --- | --- | --- |
| Elasticsearch / OpenSearch | PostgreSQL full-text search with trigram indexes is sufficient for menu and ingredient search at this cardinality | Cross-tenant search, fuzzy multilingual matching, or search latency exceeding budget |
| Service mesh (Istio, Linkerd) | Eight services do not justify the operational cost; mTLS and retries are handled at the gateway and client level | Service count above ~20, or a compliance requirement for in-cluster mTLS |
| GraphQL federation | Three BFFs are simpler to reason about, cache and debug; federation adds a distributed query planner to the failure surface | Many heterogeneous clients with divergent data needs |
| Kotlin | No additional hiring signal over Java in the target market; would add language learning on top of framework learning (ADR 0002) | Targeting employers who are Kotlin-first |
| Event sourcing for orders | The audit requirement is met by an append-only audit log and double-entry postings; full event sourcing would impose rebuild and versioning cost across the hottest aggregate | A regulatory requirement for full state reconstruction |
| CQRS everywhere | Applied only where the read and write models genuinely diverge — `can_be_served` and reporting. Applying it uniformly would double the code for no benefit | — |
| Multi-region active-active | Single region meets the stated SLOs; active-active would force conflict resolution onto the money path | An SLA requiring regional failover |

## Consequences

- Each rejection is a defensible interview answer with a stated reversal condition.
- Re-evaluation happens when a trigger fires, not when a technology becomes fashionable.
