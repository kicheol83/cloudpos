# CloudPos — Architecture Decisions

Multi-tenant restaurant POS SaaS. Target scale: 100 → 10,000 stores.
Status: pre-implementation. Every decision below should become an ADR in `docs/adr/`.

---

## 1. Capacity model

All numbers are planning estimates, stated so they can be challenged and re-measured.

**Assumptions**

| Input | Value |
| --- | --- |
| Stores (tenants) | 10,000 |
| Orders per store per day | 150 |
| Items per order | 4.2 |
| Recipe lines per dish | 5 |
| Terminals per store | 4 |
| Share of daily orders in the 4 peak hours | 60% |
| Burst factor within peak | ×3 |

**Derived load**

| Metric | Value |
| --- | --- |
| Orders / day | 1.5 M |
| Orders / sec, peak | ~190 |
| Order items / sec, peak | ~800 |
| Stock movement rows / sec, peak | ~4,000 |
| Payment calls / sec, peak | ~190 (external I/O bound) |
| Concurrent WebSocket connections | ~40,000 |

**Derived storage (per year, uncompressed, before retention policy)**

| Table | Rows / year |
| --- | --- |
| `orders` | 550 M |
| `order_items` | 2.3 B |
| `stock_movements` | 11 B |
| `payments` | 550 M |

**Consequences**

1. `stock_movements` is the system's hot spot, not `orders`. It drives the write
   architecture and the retention policy.
2. 40,000 persistent connections cannot live in the same process as request/response
   traffic. Realtime is a separately-scaled tier.
3. Payment throughput is bound by an external provider, not by us. It must be able to
   fail and degrade without taking orders down.

---

## 2. Tenancy model — the central decision

**Chosen: tiered pooled tenancy.**

- Shared schema, `tenant_id UUID` on every tenant-owned row.
- PostgreSQL Row-Level Security, `SET LOCAL app.tenant_id` set by a request interceptor
  inside the transaction. RLS is the safety net, not the primary mechanism — the
  repository layer also filters explicitly.
- `tenant_id` is the **leading column** of every primary key and every index. This makes
  every query tenant-local and keeps index locality high.
- A `tenant_placement` routing table maps `tenant_id → shard`. Day one, every tenant maps
  to shard 0. The routing indirection exists from the start so that moving a large tenant
  later is an operational task, not a rewrite.

**Rejected alternatives**

| Option | Why rejected |
| --- | --- |
| Schema per tenant | 10,000 schemas × ~40 tables ≈ 400 k `pg_class` rows. Migrations run 10,000 times per release. Autovacuum, relcache and prepared-statement cache all degrade. |
| Database per tenant | Connection count and operational cost scale linearly with tenants. Reserved for a future "dedicated" tier only. |
| Discriminator column with no RLS | One missing `WHERE tenant_id = ?` is a cross-tenant data leak. Unacceptable for a system holding customer PII and payment records. |

**Tier promotion trigger (documented, not implemented day one)**

Promote a tenant to a dedicated shard when it exceeds any of: 5% of cluster write IOPS,
50 M rows in `order_items`, or a contractual isolation requirement.

**Isolation testing.** A dedicated test suite asserts that every repository method
returns zero rows when the tenant context is set to a different tenant. This runs in CI
against Testcontainers. Cross-tenant leakage is treated as a build-breaking defect.

---

## 3. Service decomposition

Services are split by **load shape, failure domain, and compliance scope** — not by
domain noun. Eight services: six substantial, two thin.

| Service | Why it is separate | Load shape |
| --- | --- | --- |
| **identity** | Auth is a shared dependency of everything; it must stay up when other services fail. Holds staff PII. | Read-heavy, highly cacheable |
| **catalog** | Menus change rarely, are read on every terminal boot, and are the natural cache tier. | Read-heavy, near-static |
| **order** | Core transactional path. Owns the offline sync contract and idempotency. | Write-heavy, latency-sensitive |
| **inventory** | Contention centre. Recipe deduction, `can_be_served` projection, supplier requests. | Highest write volume |
| **payment** | External I/O, independent failure domain, PCI-DSS scope boundary. | Slow, bursty, retry-heavy |
| **billing** | Tax invoices, receipts, settlement, and SaaS subscription billing. Append-only, audited. | Batch + low-volume writes |
| **realtime-gateway** (thin) | Connection-bound. Scales on connection count, not request rate. | 40 k persistent connections |
| **reporting** (thin) | OLAP access pattern. Must never contend with the transactional path. | Analytical reads |

**Not separate services, and why**

- **Table & Floor** lives inside `order`. Table state changes only as a consequence of
  order and reservation events; splitting it would create a chatty synchronous dependency
  on the hottest path.
- **Reservation** lives inside `order`. It shares the table-availability invariant. Two
  services owning one invariant is how double-bookings happen.
- **Kitchen** is a projection inside `order`, pushed out through `realtime-gateway`.
- **Notification** is a consumer inside `realtime-gateway`.

Each of these has a documented extraction trigger. "We considered splitting and chose not
to, here is the metric that would change our mind" is a stronger interview answer than an
unjustified split.

**Edge**: a single API gateway (Spring Cloud Gateway or Envoy) handles routing, JWT
verification, per-tenant rate limiting and request-ID propagation. Three BFFs sit behind
it — one per frontend — so that the POS terminal is not coupled to admin-shaped payloads.

---

## 4. Technology stack

### Backend

| Concern | Choice | Rationale |
| --- | --- | --- |
| Language | Java 21 | Matches the overwhelming majority of Korean backend job descriptions. Virtual threads remove the main reason to reach for reactive. |
| Framework | Spring Boot 3.5 | Ecosystem depth: Security, Data, Kafka, Batch, Cloud Gateway. |
| Concurrency | Spring MVC + virtual threads | Blocking JDBC is the dominant cost. WebFlux would add complexity without removing that bottleneck. Reactive is used only in `realtime-gateway`. |
| Persistence | Spring Data JPA + QueryDSL, raw JDBC for hot paths | JPA for aggregates, QueryDSL for dynamic filters, plain JDBC batch for `stock_movements`. |
| Migrations | Flyway | Versioned, reviewable, expand/contract friendly. |
| Build | Gradle (Kotlin DSL), multi-module | |
| API contract | OpenAPI 3, generated clients | Frontend types are generated, never hand-written. |
| Inter-service sync calls | gRPC where latency matters, REST elsewhere | |

### Data stores

| Store | Use |
| --- | --- |
| PostgreSQL 17 | Primary store, one logical database per service |
| Redis 7 | Cache, idempotency fast path, rate limiting, distributed locks, WebSocket pub/sub fanout |
| Kafka | Event backbone between services |
| ClickHouse | Reporting service read store |
| S3 / MinIO | Dish images, invoice PDFs, settlement files |

### Frontend

| App | Stack | Notes |
| --- | --- | --- |
| POS terminal | React 19 + TypeScript + Vite, PWA | Tablet landscape 1194×834. Offline-first: IndexedDB (Dexie) + Service Worker + custom sync engine. |
| Merchant admin | Next.js (App Router) | Store owner: menus, recipes, staff, reports. |
| Platform admin | Next.js | Tenant provisioning, plan management, operational dashboards. |
| Shared | pnpm + Turborepo monorepo | Packages: `tokens` (generated from Figma), `ui`, `api-client` (generated from OpenAPI), `types`. |
| State | TanStack Query + Zustand | Server state and client state kept separate. |

Design tokens are generated from the Figma file into CSS custom properties in two layers
(primitive → semantic), so dark mode is a single attribute switch.

### Infrastructure

| Concern | Choice |
| --- | --- |
| Runtime | Kubernetes (k3s for the portfolio deployment) |
| Delivery | GitHub Actions → GHCR → ArgoCD (GitOps) |
| IaC | Terraform |
| Observability | OpenTelemetry → Prometheus, Tempo, Loki, Grafana |
| Secrets | External Secrets Operator + SOPS |
| Load testing | k6, run in CI with committed result artefacts |

**Deliberately rejected**: Elasticsearch (PostgreSQL full-text search is sufficient for
menu search at this scale), a service mesh (operational cost far exceeds the benefit at
eight services), and GraphQL federation (three BFFs are simpler and more debuggable).
Restraint is itself an architectural decision and is documented as one.

---

## 5. Data architecture

### Money

- Stored as `BIGINT` minor units plus an ISO-4217 `currency` column. KRW has zero decimal
  places, so one unit is one won. Never floating point.
- A single `Money` value object in shared code; arithmetic is not permitted on raw longs.
- Rounding rules for tax are defined once, in `billing`, and applied nowhere else.

### Time

- All timestamps are `timestamptz`, stored in UTC.
- Every store has an IANA timezone and a **business-day cutoff** (e.g. 05:00 local).
- Every transactional row carries a denormalised `business_date DATE`, computed at write
  time. Reports group by `business_date`, never by `created_at::date`. Without this,
  every daily figure is wrong for late-night trade — and every reconciliation breaks.

### Partitioning

| Table | Strategy |
| --- | --- |
| `orders`, `order_items`, `payments` | `RANGE (business_date)`, monthly, automated with `pg_partman` |
| `stock_movements` | `RANGE (business_date)`, weekly — highest volume, shortest retention |
| `audit_log` | `RANGE (created_at)`, monthly |

Partition pruning requires `business_date` in the predicate; the repository layer enforces
that every query on a partitioned table carries both `tenant_id` and a date range.

### Retention

`stock_movements` at 11 B rows/year is not economically storable at full granularity.

- Raw movements: 90 days hot.
- After 90 days: aggregate to `stock_daily_summary` (tenant, store, ingredient,
  business_date, opening, in, out, waste, closing), drop the raw partition.
- Orders and payments: 5 years hot (Korean bookkeeping retention), then cold storage in
  Parquet on S3.
- Reservation customer PII: purge 1 year after the reservation date.

### Write path for stock

The naive design — one `INSERT` per recipe line per order item, synchronously — produces
4,000 writes/sec at peak and lock contention on popular ingredients.

Instead:

1. At order confirmation, the deduction is computed and written as **one batched
   multi-row insert** per order, via JDBC batch.
2. Ingredient balance is maintained in an `ingredient_stock` row updated with a single
   `UPDATE ... SET qty = qty - ?` per ingredient, ordered by `ingredient_id` across the
   whole batch to give a deterministic lock order and prevent deadlocks.
3. `dish.can_be_served` is a **projection**, recomputed asynchronously from
   `stock.changed` events, not on the read path. It is allowed to be a few seconds stale,
   and the UI treats it as advisory — the authoritative check happens at order
   confirmation.

This split (advisory read model, authoritative write-time check) is the core consistency
decision of the inventory domain and should be its own ADR.

### Concurrency strategy

| Case | Mechanism | Reason |
| --- | --- | --- |
| Ingredient stock deduction | `SELECT ... FOR UPDATE` in fixed ID order | High contention, short transaction |
| Table state transition | Optimistic locking (`@Version`) | Low contention, conflict is meaningful and should surface to the user |
| Reservation slot booking | Exclusion constraint on `(tenant_id, table_id, tstzrange)` with `btree_gist` | Lets the database enforce no-double-booking rather than application code |
| Invoice number allocation | Per-tenant counter row with `FOR UPDATE` | Gapless sequence is a legal requirement; PostgreSQL sequences leak gaps on rollback |

### Connection management

PgBouncer in transaction pooling mode. Note the JDBC interaction: server-side prepared
statements must be disabled (`prepareThreshold=0`) or PgBouncer must run in session mode
for those pools. This is a real trap and belongs in an ADR.

Read replicas serve `reporting` ingestion and admin analytics. The transactional path
never reads from a replica — stale reads on order state are user-visible bugs.

---

## 6. Consistency and messaging

**Principle: money and stock are never eventually consistent within a single business
operation. Everything else may be.**

- **Transactional outbox** in every service. The domain write and the event row commit in
  one local transaction. Debezium reads the WAL and publishes to Kafka. (A polling
  publisher is the fallback if Debezium is too much operational weight; the trade-off is
  documented.)
- **Kafka topics** are keyed by `store_id` so that per-store ordering is preserved.
  Partition count is sized for 10,000 stores with room to grow; consumers are idempotent
  by `event_id`, deduplicated in Redis with a Postgres backstop.
- **Sagas** are orchestrated, not choreographed, for the two flows where a human is
  waiting and failure must be explainable: order → payment → invoice, and
  cancel → refund → stock restore. Orchestration state lives in a `saga_instance` table
  so a stuck saga is queryable, not archaeological.
- **Idempotency**: every mutating public endpoint accepts an `Idempotency-Key`. The key,
  the request fingerprint and the serialised response are stored; a replay returns the
  stored response. This is what makes offline replay and network retries safe.

---

## 7. Payment — real integration

**Provider**: PortOne (formerly 아임포트) as the aggregator, with Toss Payments as the
first concrete PG. Both offer sandbox credentials suitable for a portfolio. Stripe test
mode is added as a second adapter to prove the abstraction is real.

**Design**

- `PaymentGateway` port with one adapter per provider. Provider choice is per-tenant
  configuration, not a compile-time constant.
- Every payment attempt gets a client-generated idempotency key that survives retries.
- Webhooks: signature verified, stored raw before processing, processed idempotently by
  provider transaction ID. Out-of-order and duplicate webhooks are assumed, not excluded.
- Async methods (QR, virtual account) use a state machine with an explicit expiry and a
  reconciliation sweeper, not an open polling loop from the terminal.
- **Daily reconciliation job**: pull the provider's settlement file, compare line by line
  against internal payment records, write discrepancies to an exceptions table for human
  review. This is the single most production-realistic component in the whole system and
  is worth building properly.

**PCI-DSS scope**: card data never touches our servers. The terminal uses the provider's
hosted payment UI or SDK; we store only a provider token and the last four digits. The
scope boundary is stated explicitly in `docs/security/pci-scope.md` — knowing what is
*out* of scope and why is the senior signal here.

---

## 8. Invoice and tax — real

Two distinct concepts that are frequently conflated:

1. **Customer receipt** — issued per order, always.
2. **Tax document** — 현금영수증 (cash receipt) and 세금계산서 (tax invoice), filed with the
   국세청. Issued through a provider such as Popbill or Barobill, both of which have test
   environments.

**Decisions**

- VAT is 10% in Korea, computed and rounded in `billing` only, with the rounding rule
  fixed and unit-tested against known cases.
- Invoice numbering is **gapless per tenant per fiscal year**. PostgreSQL sequences are
  not acceptable because a rolled-back transaction consumes a number. A counter row taken
  under `FOR UPDATE` inside the issuing transaction is the correct trade-off: the
  serialisation cost is acceptable at invoice volume, and the legal requirement is met.
- Issued invoices are **immutable**. Corrections are new documents that reference the
  original (credit note), never updates. Enforced with a database trigger rejecting
  `UPDATE` and `DELETE` on the issued-invoice table.
- PDFs are rendered once, hashed (SHA-256), and stored in object storage with the hash in
  the database. Re-rendering must reproduce the same hash.
- Settlement uses an internal append-only double-entry posting table. Every order,
  payment, refund, discount and tax amount produces balanced postings. A daily job
  asserts that all postings sum to zero and that the closing balance matches the
  materialised snapshot. A failed assertion pages, it does not warn.

---

## 9. Realtime

- Protocol: WebSocket with STOMP over Spring, one connection per terminal.
- The gateway is stateless; subscription routing goes through Redis pub/sub so any pod can
  serve any store. Sticky sessions are not required.
- ~10,000 connections per pod; 4–6 pods at target scale, autoscaled on connection count
  rather than CPU.
- Delivery is at-least-once with client-side deduplication by event ID. Terminals
  reconcile with a full state fetch on reconnect — the socket is an optimisation, never
  the source of truth.
- Channels: kitchen dish status, table state, low-stock alerts, order updates.

---

## 10. Offline-first terminal

The single most differentiating feature, and the reason the order contract is shaped as it
is.

- Orders are created client-side with a UUIDv7 `client_order_id` and queued in IndexedDB.
- On reconnect, a batch endpoint replays the queue. The server deduplicates on
  `(tenant_id, client_order_id)` with a unique constraint.
- Menu and price data are cached locally with a version stamp; a stale terminal is
  detected on sync and forced to refresh before it may submit.
- **Conflict policy**: if stock is insufficient at replay time, the order is *not*
  rejected — it is accepted and a negative stock adjustment plus an alert is recorded.
  Refusing a sale that physically already happened would put the system out of step with
  reality. This mirrors how real POS systems behave and should be stated prominently.
- Payments are never taken offline for card or QR. Cash sales sync; electronic payments
  require connectivity. The boundary is explicit.

---

## 11. Security and compliance

- **AuthN**: staff sign in with a store-scoped PIN on a device that has been registered
  and holds a device credential. A PIN alone is not a credential — device identity plus
  PIN is. PINs are Argon2id hashed, rate limited per device, and locked out with backoff.
- **AuthZ**: role-based (Owner, Manager, Waiter, Kitchen) evaluated per tenant. Permission
  checks are declarative at the method level, not scattered through controllers.
- **Tokens**: short-lived access JWT, rotating refresh token with reuse detection.
- **PII**: customer names and phone numbers in reservations fall under the Korean
  Personal Information Protection Act. Encrypted at rest at column level, access audited,
  retention-limited, with an implemented deletion path. This is a compliance requirement,
  not a nice-to-have.
- **Audit log**: append-only, covering permission changes, price changes, refunds,
  discounts and inventory adjustments — the fraud-sensitive operations in any POS.
- **Tenant isolation** is verified by an automated test suite, as described in §2.

---

## 12. Reliability

**SLOs** (defined up front so that error budgets mean something)

| Flow | Objective |
| --- | --- |
| Order creation | 99.9% availability, p99 < 300 ms |
| Payment initiation | 99.5% availability, p99 < 2 s (bounded by provider) |
| Menu read | 99.95% availability, p99 < 100 ms |
| Realtime delivery | 99% of events delivered within 2 s |

**Degradation ladder.** When `payment` is down, orders are still taken and marked
unpaid. When `inventory` is down, orders are still taken and stock is reconciled on
recovery. When `catalog` is down, terminals serve from local cache. The terminal must
never be blocked from taking money by a non-essential service — this is stated as a
design constraint, not discovered later.

**Disaster recovery**: RPO 5 minutes, RTO 1 hour. Continuous WAL archiving with
point-in-time recovery; restore drills are scripted and run in CI monthly.

**Zero-downtime migrations**: strict expand/contract. Add nullable column → backfill in
batches → dual-write → switch reads → drop old column, each as a separate release. No
release may contain a backward-incompatible schema change.

---

## 13. Build plan

Each phase ends with something demonstrable. Phases are not parallelisable by one person.

| Phase | Scope | Estimate |
| --- | --- | --- |
| 0 | Walking skeleton: monorepo, Gradle multi-module, Flyway, Testcontainers, CI, one service end-to-end through the gateway | 3 weeks |
| 1 | `identity` + tenancy foundation: RLS, tenant routing, device + PIN auth, RBAC, isolation test suite | 4 weeks |
| 2 | `catalog` + `order` (single service scope: tables, floors, orders, reservations) with the full concurrency model | 6 weeks |
| 3 | `inventory`: recipes, batched deduction, `can_be_served` projection, k6 contention benchmarks with published numbers | 5 weeks |
| 4 | Kafka + outbox + sagas; extract the first cross-service flow properly | 4 weeks |
| 5 | `payment`: real PG sandbox, webhooks, refunds, reconciliation job | 5 weeks |
| 6 | `billing`: tax invoice provider, gapless numbering, double-entry postings, PDF pipeline | 5 weeks |
| 7 | `realtime-gateway` + POS terminal offline sync engine | 6 weeks |
| 8 | `reporting` (ClickHouse), merchant admin, platform admin | 6 weeks |
| 9 | Kubernetes, ArgoCD, observability, load-test report, DR drill, ADR completion | 4 weeks |

Roughly 11–12 months at a sustainable solo pace. If that is too long, the right cut is to
drop phases 8 and 9 to a minimal form — not to build every service shallowly.

---

## 14. What makes this read as senior

Code quality is assumed. These are the things that distinguish the project:

1. Capacity estimates with stated assumptions, and an architecture that visibly follows
   from them.
2. Options rejected in writing, with the metric that would reverse the decision.
3. A retention and archival policy, because 11 B rows a year is a cost decision.
4. A stated compliance scope: what PCI data we deliberately never touch, and how PIPA
   obligations are met.
5. SLOs and a degradation ladder defined before the first line of code.
6. Load test results committed to the repository as artefacts, not claimed in prose.
7. A migration strategy that permits zero-downtime releases.
8. Restraint: the technologies deliberately *not* adopted, and why.
