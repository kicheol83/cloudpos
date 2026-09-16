# Architecture Decision Records

CloudPos — multi-tenant restaurant POS SaaS. Target scale: 10,000 stores.

Format: Context, Decision, Consequences, Alternatives considered. ADRs are immutable once
accepted; a changed decision becomes a new ADR that supersedes the old one.

| # | Decision | Area |
| --- | --- | --- |
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Process |
| [0002](0002-java-spring-boot-virtual-threads.md) | Java 21 + Spring Boot, virtual threads | Platform |
| [0003](0003-service-decomposition.md) | Service boundaries by load shape and failure domain | Architecture |
| [0004](0004-multi-tenancy-pooled-with-rls.md) | Pooled multi-tenancy with RLS and tiered promotion | Data |
| [0005](0005-postgresql-per-service.md) | PostgreSQL 17, one database per service | Data |
| [0006](0006-money-and-time.md) | Money and time representation | Data |
| [0007](0007-partitioning-and-retention.md) | Partitioning and retention | Data |
| [0008](0008-concurrency-control.md) | Concurrency control per contention profile | Data |
| [0009](0009-outbox-and-kafka.md) | Transactional outbox and Kafka topic design | Messaging |
| [0010](0010-orchestrated-sagas.md) | Orchestrated sagas for the money paths | Messaging |
| [0011](0011-idempotency-and-offline-replay.md) | Idempotency and offline order replay | Contract |
| [0012](0012-payment-portone-v2.md) | PortOne V2 payment gateway; PCI scope | Payment |
| [0013](0013-tax-documents-and-invoice-numbering.md) | Korean tax documents; gapless invoice numbering | Billing |
| [0014](0014-realtime-websocket-tier.md) | Separately scaled WebSocket tier | Realtime |
| [0015](0015-clickhouse-for-reporting.md) | ClickHouse as the reporting store | Analytics |
| [0016](0016-kubernetes-argocd-and-reliability.md) | Kubernetes, ArgoCD, SLOs and degradation ladder | Operations |
| [0017](0017-rejected-technologies.md) | Technologies deliberately not adopted | Architecture |
| [0018](0018-local-development-environment.md) | Local development environment | Process |
| [0019](0019-monorepo-and-ci.md) | Monorepo, path-filtered CI, separate deploy repo | Process |
| [0020](0020-write-then-fail-pattern.md) | Transactional methods that record a failure must not throw | Data |

## Reading order for a reviewer

Start with 0003 (what the services are), then 0004 and 0007 (how the data is shaped by
scale), then 0008 and 0010 (how correctness is maintained under concurrency and across
services). 0017 is the shortest path to understanding the judgement applied throughout.

## Related documents

- `docs/architecture.md` — capacity model and full stack overview
- `docs/design-analysis.md` — Figma UI kit analysis and screen inventory
- `docs/domain-model.md` — aggregates, invariants and schema per service
- `docs/contracts.md` — event catalog, API conventions, offline sync contract
- `docs/security/pci-scope.md` — payment data scope boundary (ADR 0012)
