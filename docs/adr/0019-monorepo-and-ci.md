# ADR 0019: Monorepo with path-filtered CI, and a separate deployment repository

**Status:** Accepted
**Date:** 2026-09-05

## Context

Microservices are frequently assumed to require one repository per service. That
assumption conflates two different things: **independent deployability**, which is the
actual goal, and **independent version control**, which is not.

With nine repositories, a single contract change — producer, consumer, contract test —
becomes three pull requests that cannot be merged atomically. For one engineer, that is
pure overhead with no compensating benefit.

## Decision

**One monorepo**, `cloudpos/`:

```
cloudpos/
  contracts/        OpenAPI documents and event schemas — the source of truth
  services/         Gradle multi-project
                      gateway/ identity/ catalog/ order/ inventory/
                      payment/ billing/ realtime-gateway/ reporting/
  apps/             pnpm workspace
                      pos-terminal/ merchant-admin/ platform-admin/
  packages/         tokens/ ui/ api-client/ (generated) types/
  infra/            terraform/ helm/ compose/
  tools/            seed/ loadtest/ (k6)
  docs/             adr/ architecture.md domain-model.md contracts.md design-analysis.md
```

**Independent deployability is preserved.** Each service builds its own image, tagged with
the git SHA, and deploys on its own schedule. Sharing a repository does not share a
release cycle.

**Path-filtered CI.** A change under `services/order/` builds, tests and publishes only
`order`, plus anything whose contract tests reference it. A change under `contracts/`
builds every affected producer and consumer — which is exactly the case where an atomic
change matters.

**Boundaries are enforced, not trusted.** A monorepo makes it trivially easy to import
another service's internals. Two mechanisms prevent it:

- Gradle module dependencies: a service may depend on `contracts` and shared libraries
  only, never on another service's project.
- ArchUnit rules in every service's test suite, failing the build on a cross-service
  package import.

**Shared configuration through convention plugins** in `buildSrc`, so that Testcontainers,
ArchUnit, JaCoCo, OpenTelemetry and the Flyway setup are identical everywhere and
configured once. Dependency versions come from a single Gradle version catalog
(`libs.versions.toml`).

**Generated code is not committed.** `packages/api-client` and the Java client stubs are
produced at build time from `contracts/`. Committing them invites drift and produces
noisy diffs.

**One exception to the monorepo: a separate `cloudpos-deploy` repository** holding the
ArgoCD manifests and Helm values. CI writes the new image tag there after a successful
build, and ArgoCD reconciles from it. Keeping deployment state in the application
repository would make CI commit to the repository that triggers CI — a loop that has to be
suppressed with commit-message hacks. Separation is cleaner and is the standard GitOps
arrangement.

**Branching**: trunk-based. Short-lived branches, no long-lived `develop`. Conventional
commits. Merge to `main` → CI builds → ArgoCD deploys to staging → manual promotion to
production.

## Consequences

- Checkout size grows over time; shallow clones and Gradle build caching keep CI fast.
- Path filtering must be correct or CI will silently skip tests it should have run. The
  filter configuration is itself reviewed and is tested by a deliberate "touch a contract,
  observe the full fan-out" check.
- Two repositories to manage instead of one. Accepted for the GitOps loop.
- A reviewer can read the entire system — contracts, services, frontends, infrastructure —
  in one place. For a portfolio project this is a significant advantage.

## Alternatives considered

| Option | Rejected because |
| --- | --- |
| One repository per service | Non-atomic contract changes; nine CI configurations; nine dependency version sets; no compensating benefit at this team size. |
| Monorepo including deployment manifests | CI commits trigger CI. Workable with suppression hacks, but fragile. |
| Committing generated clients | Drift between contract and client becomes possible and invisible. |
