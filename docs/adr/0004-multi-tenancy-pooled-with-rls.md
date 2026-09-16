# ADR 0004: Pooled multi-tenancy with RLS and tiered promotion

**Status:** Accepted
**Date:** 2026-09-05

## Context

Target scale is 10,000 tenants. The tenancy model determines schema design, migration
strategy, connection management and the blast radius of any isolation bug.

## Decision

**Pooled model with a promotion path.**

- Shared schema. Every tenant-owned row carries `tenant_id UUID`.
- `tenant_id` is the **leading column** of every primary key and every index, keeping all
  queries and index scans tenant-local.
- PostgreSQL Row-Level Security enabled on every tenant table. `SET LOCAL app.tenant_id`
  is applied by an interceptor inside the transaction. RLS is the safety net; the
  repository layer also filters explicitly.
- A `tenant_placement` table maps `tenant_id → shard`. On day one every tenant maps to
  shard 0. The indirection exists from the start so that relocating a tenant later is an
  operational task rather than a rewrite.

**Promotion trigger** to a dedicated shard: a tenant exceeding 5% of cluster write IOPS,
or 50 M rows in `order_items`, or a contractual isolation requirement.

**Isolation testing:** a CI suite asserts that every repository method returns zero rows
when the tenant context is set to a different tenant. Cross-tenant leakage breaks the
build.

## Consequences

- One migration per release, not 10,000.
- Noisy-neighbour risk is real and is mitigated by per-tenant rate limiting at the
  gateway and by the promotion path.
- Every table gains a column and every index gains a leading column; composite index
  design must be deliberate.
- Connection pooling stays viable because there is one schema.

## Alternatives considered

| Option | Rejected because |
| --- | --- |
| Schema per tenant | 10,000 schemas × ~40 tables ≈ 400 k `pg_class` rows. Migrations run 10,000 times per release. Relcache, autovacuum and prepared-statement caches degrade. |
| Database per tenant | Connection count and operational cost scale linearly with tenants. Reserved for a future dedicated tier. |
| `tenant_id` column with no RLS | A single missing predicate is a cross-tenant data leak in a system holding customer PII and payment records. |
