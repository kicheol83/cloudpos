# CloudPos — Build Plan

How the system gets built, commit by commit. Companion to `docs/adr/`,
`docs/domain-model.md` and `docs/contracts.md`.

---

## 1. Commit discipline

**One commit is one logical change, and it compiles and passes tests.** A commit that
leaves the build red is never pushed. The commit history is read by reviewers; it is a
record of how the system was reasoned about, not a backup log.

Conventional Commits, scoped by service:

```
feat(identity): issue access and refresh tokens on PIN login
fix(order): lock ingredients in ascending id order to avoid deadlock
refactor(inventory): extract recipe deduction into its own service
test(catalog): assert price history has no overlapping periods
docs(adr): record why reservations live inside the order service
chore(ci): add path filter for the billing service
perf(inventory): batch stock movement inserts into a single statement
```

Scopes: `identity`, `catalog`, `order`, `inventory`, `payment`, `billing`, `realtime`,
`reporting`, `gateway`, `terminal`, `admin`, `contracts`, `infra`, `ci`, `adr`.

Rules:

- Migrations ship in the same commit as the code that needs them.
- A contract change ships with its producer, its consumers and its contract test — the
  atomic change the monorepo exists to permit (ADR 0019).
- An ADR is committed **before** the code it governs, not after.
- No commit mixes a refactor with a behaviour change.

---

## 2. Estimated commit volume

| Area | Commits |
| --- | --- |
| Backend services | ~350 |
| Frontend (terminal + two admins) | ~200 |
| Infrastructure, CI, docs | ~50 |
| **Total** | **~600** (range 500–700) |

Per phase, roughly 25–75. The number is not a target; it falls out of the sizing rule
above.

---

## 3. How every service is created

The same eleven steps, every time. After the second service this is mechanical — which
is exactly why the convention plugins were written in phase 0.

1. **Contract first.** OpenAPI document and event schemas into `contracts/`.
2. **Gradle module.** Two lines: the convention plugin and the module include.
3. **Migration V1.** Tables, `ENABLE` + `FORCE ROW LEVEL SECURITY`, tenant policies,
   grants to `cloudpos_app`.
4. **Entities and repositories.** No `tenant_id` parameters anywhere.
5. **Service layer.** The transactional boundary, where the RLS aspect binds.
6. **Controller.** Problem-details errors, idempotency header on mutations.
7. **Outbox table and publisher.**
8. **Tenant isolation test suite.** Copied from the phase 0 pattern, adapted to this
   service's tables. Build-breaking on failure.
9. **Contract tests** against every registered consumer.
10. **Wiring.** Compose profile entry, gateway route, stub registration.
11. **Operability.** Health, readiness, OpenTelemetry, declared SLO and dependency
    failure behaviour.

---

## 4. How services communicate

Three mechanisms, chosen deliberately per case.

| Mechanism | When | Example |
| --- | --- | --- |
| Synchronous call (gRPC) | An answer is needed inside a transaction that must not be eventually consistent | `order → inventory` stock deduction at order confirmation |
| Kafka event | A fact happened; consumers decide what to do | `stock.changed` → availability projection, reporting |
| Saga command | A step in a multi-service business transaction with compensation | `order → payment → billing` settlement |

**The subtle one.** Stock deduction at order confirmation is *synchronous*, not an event.
Our own consistency rule (ADR 0010) says stock is never eventually consistent within a
single business operation — routing it through Kafka would produce oversell. The event
`stock.changed` is emitted *after* the transaction commits, and feeds only projections
and reporting.

**Revision to the earlier plan.** Kafka and the outbox were originally scheduled for
phase 4. That was wrong: the first moment two services must exchange a fact is phase 3,
when `inventory` appears. Introducing messaging later would mean rewriting `order`.
Kafka now lands with `inventory`.

**No cross-service joins, ever.** Where a service needs another's data for its own
queries, it keeps a local replica maintained from events — for example `inventory` holds
`(dish_id, dish_name, is_active)` replicated from `catalog`. Replica columns are marked
as such and are never written locally.

---

## 5. Phase map

Each phase ends with something demonstrable. Backend leads, frontend follows within the
same phase.

| Phase | Backend | Frontend | Commits |
| --- | --- | --- | --- |
| 0 ✅ | Skeleton, gateway, identity, RLS tenancy | — | ~8 |
| 1 | Device registration, PIN auth (Argon2id, lockout), JWT + refresh rotation, RBAC, shift, tenant provisioning, real JWT in gateway | Design token package, PIN login, employee select, forgot PIN | ~55 |
| 2 | Catalog (dishes, categories, options, price history, store overrides, menu version) + Order (floor plan, tables, sessions, orders, kitchen status, reservations) | Menu, cart, order details, table map, reservation flow | ~120 |
| 3 | Inventory (ingredients, recipes, batched deduction, availability projection, purchase requests) **+ outbox + Kafka** | Inventory screens, availability badges, low-stock alerts | ~85 |
| 4 | Orchestrated sagas, `saga_instance` support surface | Order lifecycle states, void and approval flows | ~45 |
| 5 | Payment: PortOne V2, webhooks, refunds, reconciliation job | Cash, card, QR and virtual account flows | ~70 |
| 6 | Billing: receipts, 현금영수증, 세금계산서, gapless numbering, double-entry ledger, shift settlement | Receipt, shift close, settlement views | ~70 |
| 7 | Realtime gateway, WebSocket channels | **Offline sync engine**, kitchen status, live table map | ~80 |
| 8 | Reporting (ClickHouse), read models | Dashboard, history, merchant admin, platform admin | ~75 |
| 9 | Kubernetes, ArgoCD, observability, load tests, DR drill | Polish pass across all screens | ~50 |

---

## 6. Frontend scope

The full UI kit is being implemented. One clarification that changes how the work is
counted: **91 Figma frames are not 91 screens.** Most are states of the same route.

- "Menu Empty" and "Menu Filled" — one route, two states
- "Add New Table / Table Info 1–3" — one wizard, three steps
- "QR Code / Pay → Process → Success Payment" — one flow, three states
- Every screen exists twice, light and dark — one implementation, two token modes

The real shape is roughly **25 routes covering ~90 states**, each in both themes.
Building all of them means every state is implemented, not that 91 separate pages are
written. A route with half its states built looks unfinished regardless of how polished
the happy path is, so states are treated as part of the route, not as optional extras.

Route groups, mapped from the kit:

| Group | Routes | States |
| --- | --- | --- |
| Authentication | 2 | 4 |
| Dashboard | 1 | 3 |
| Order list | 1 | 3 |
| Create order (dine-in, take-away) | 4 | 18 |
| Table and floor management | 5 | 18 |
| Reservation | 2 | 11 |
| Payment | 3 | 9 |
| Account settings | 4 | 9 |
| History | 1 | 2 |
| Inventory (menu, ingredients) | 4 | 11 |

Design tokens are generated from the Figma file into a two-layer CSS variable system
(primitive → semantic), so dark mode costs one attribute switch rather than a second
implementation.

---

## 7. Phase 1 commit sequence

The immediate next phase, in order. Each line is one commit.

**Foundation**

1. `chore(infra)`: move local port configuration into `application-local.yml`
2. `docs`: add architecture, domain model, contracts and ADRs to `docs/`
3. `chore(ci)`: enable path-filtered CI for all planned services

**Tenant and store provisioning**

4. `feat(identity)`: tenant provisioning endpoint and lifecycle status
5. `feat(identity)`: store CRUD with timezone and business-day cutoff
6. `test(identity)`: extend isolation suite to stores and tenants

**Device identity**

7. `feat(identity)`: device entity, migration and registration endpoint
8. `feat(identity)`: device credential issuance and revocation
9. `test(identity)`: revoked device is rejected

**Staff and PIN**

10. `feat(identity)`: staff entity with role and employment status
11. `feat(identity)`: Argon2id PIN hashing
12. `feat(identity)`: PIN verification with failed-attempt counter and lockout
13. `test(identity)`: lockout engages and expires correctly

**Tokens**

14. `feat(identity)`: JWT access token with tid, sid, did, role, shift claims
15. `feat(identity)`: refresh token issuance with rotation
16. `feat(identity)`: refresh token reuse detection
17. `test(identity)`: reused refresh token invalidates the family

**Authorization**

18. `feat(identity)`: role-based method security
19. `feat(identity)`: manager approval endpoint for privileged operations
20. `test(identity)`: role matrix coverage

**Shift**

21. `feat(identity)`: shift entity and open endpoint
22. `feat(identity)`: one-open-shift-per-staff partial unique index
23. `feat(identity)`: shift close with unsynced-order guard
24. `test(identity)`: concurrent shift opens conflict correctly

**Gateway**

25. `feat(gateway)`: verify access token and extract the tid claim
26. `feat(gateway)`: reject requests whose did claim mismatches `X-Device-Id`
27. `feat(gateway)`: per-tenant rate limiting
28. `test(gateway)`: client-supplied `X-Tenant-Id` is stripped

**Contracts and docs**

29. `feat(contracts)`: identity OpenAPI document
30. `feat(contracts)`: identity event schemas
31. `docs(adr)`: record device + PIN as the authentication credential

**Frontend**

32–55. Design token package generated from Figma, then the authentication route group:
employee select, PIN entry with all states, forgot PIN, lockout, both themes.
