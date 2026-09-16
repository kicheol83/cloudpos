# ADR 0016: Kubernetes with ArgoCD, and the reliability contract

**Status:** Accepted
**Date:** 2026-09-05

## Context

Eight services, three frontends, four datastores and a broker. Deployment must be
reproducible, and the reliability characteristics must be stated before implementation so
that they constrain design rather than describe whatever emerged.

## Decision

**Delivery**

- Kubernetes (k3s for the portfolio deployment), Helm charts per service.
- GitHub Actions builds and pushes to GHCR; **ArgoCD** reconciles from a Git repository.
  Deployment state is Git state.
- Terraform for infrastructure; External Secrets Operator with SOPS for secrets.
- Observability: OpenTelemetry to Prometheus, Tempo and Loki, visualised in Grafana.
  Trace IDs propagate from the terminal through the gateway to every service.

**SLOs**

| Flow | Objective |
| --- | --- |
| Order creation | 99.9% availability, p99 < 300 ms |
| Payment initiation | 99.5% availability, p99 < 2 s (provider-bound) |
| Menu read | 99.95% availability, p99 < 100 ms |
| Realtime delivery | 99% of events within 2 s |

**Degradation ladder** — a design constraint, not a discovered behaviour:

- `payment` unavailable → orders are still taken and marked unpaid.
- `inventory` unavailable → orders are still taken; stock reconciles on recovery.
- `catalog` unavailable → terminals serve from local cache.
- `realtime-gateway` unavailable → terminals fall back to polling.

**The terminal must never be blocked from taking money by a non-essential service.**

**Disaster recovery:** RPO 5 minutes, RTO 1 hour. Continuous WAL archiving with
point-in-time recovery. Restore drills are scripted and run monthly in CI.

**Zero-downtime migrations:** strict expand/contract. Add nullable column → backfill in
batches → dual-write → switch reads → drop old column, each a separate release. No
release may contain a backward-incompatible schema change.

## Consequences

- Kubernetes adds meaningful operational surface for a solo project; it is scoped to a
  late phase so that it cannot block core development.
- SLOs must be measured, with dashboards and alerts, or they are decoration.
- The degradation ladder forces every synchronous call to have a defined failure
  behaviour, which shapes the API design from the start.
