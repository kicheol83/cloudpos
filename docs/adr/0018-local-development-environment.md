# ADR 0018: Local development environment

**Status:** Accepted
**Date:** 2026-09-05

## Context

The system is eight backend services, three frontends, PostgreSQL, Redis, Kafka,
Kafka Connect and ClickHouse. Running all of it on one machine is the default assumption
and it does not survive contact with actual memory limits.

Rough measurement of a full stack:

| Component | Memory |
| --- | --- |
| 8 Spring Boot services (dev mode) | ~4.8 GB |
| PostgreSQL | ~0.5 GB |
| Kafka (KRaft, no ZooKeeper) | ~1.0 GB |
| Kafka Connect + Debezium | ~1.0 GB |
| ClickHouse | ~1.0 GB |
| Redis | ~0.1 GB |
| 3 Vite dev servers | ~1.5 GB |
| **Total** | **~10 GB**, before the OS and an IDE |

A 16 GB machine cannot run this and remain usable. The problem is not the peak; it is that
developers stop running tests locally when the loop is slow, and quality follows.

## Decision

**Docker Compose with profiles. Never run the whole stack.**

| Profile | Contents |
| --- | --- |
| `core` | PostgreSQL, Redis, gateway, identity |
| `menu` | + catalog |
| `sales` | + order |
| `stock` | + inventory |
| `money` | + payment, billing, Kafka, Connect |
| `realtime` | + realtime-gateway |
| `analytics` | + ClickHouse, reporting |
| `full` | everything — CI and demo only |

Wrapped in a `Makefile`: `make up.sales`, `make down`, `make logs.order`.

**One PostgreSQL container, eight logical databases.** ADR 0005 requires a logical
database per service, not a cluster per service. Locally they share one instance with
separate databases, users and Flyway histories. Production separates clusters; the code
cannot tell the difference because it never crosses a database boundary anyway.

**The service under development runs from the IDE, not from Compose.** Compose provides
infrastructure and the services you are *not* editing. This preserves the debugger and
hot reload where it matters.

**Absent services are replaced by contract stubs.** Spring Cloud Contract Stub Runner
serves WireMock stubs generated from the same contracts that produce the contract tests
(see `docs/contracts.md` §8). Working on `order` therefore needs `core` + `menu` +
`sales` — three services, not eight.

**External providers are stubbed by default.** PortOne and Popbill sandboxes are reached
only from a dedicated `integration` environment and from a nightly CI job. Local
development uses recorded stubs, so the loop does not depend on a third party's
availability or rate limits.

**Testcontainers with reuse enabled** (`testcontainers.reuse.enable=true`) for integration
tests, so container startup is paid once per session rather than once per test class.

**Seed data** is produced by a `dev-seed` container running the generator from
`tools/seed` — never by hand-written SQL fixtures that drift from the schema.

## Consequences

- Minimum workable machine: 16 GB with profiles. Comfortable: 32 GB.
- Stubs must stay honest. A stub that diverges from the real service is worse than no
  stub, which is why they are generated from contracts rather than written by hand.
- `full` is exercised in CI on every merge, so the fact that nobody runs it locally does
  not mean it is broken.

## Alternatives considered

| Option | Rejected because |
| --- | --- |
| Local Kubernetes (Tilt, Skaffold, k3d) | Adds Kubernetes debugging on top of application debugging. Kubernetes is a deployment target (ADR 0016), not a development environment. |
| Run everything in Compose, always | Does not fit in 16 GB and destroys the feedback loop. |
| Shared remote development environment | Introduces contention and network latency into the inner loop, and costs money continuously. |
| One PostgreSQL container per service locally | Eight instances for no benefit; logical separation already satisfies ADR 0005. |
