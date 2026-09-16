# CloudPos — Code Conventions

What files exist, where they live, and what they are called. Applies to every service.
Companion to `docs/build-plan.md` §3.

---

## 1. Package by aggregate, not by layer

There are no `controller/`, `service/` or `repository/` packages. Each aggregate owns a
package containing everything that belongs to it.

```
io.cloudpos.order
├── OrderApplication.java
├── config/
├── order/            ← aggregate
├── session/          ← aggregate
├── table/            ← aggregate
├── reservation/      ← aggregate
├── outbox/           ← service infrastructure
└── idempotency/      ← service infrastructure
```

Reason: `order` holds four aggregates. Organised by layer, a single change means jumping
between four packages. Organised by aggregate, a change stays in one place, and the
package boundary mirrors the domain boundary.

---

## 2. Files inside one aggregate package

Using `order/` as the worked example. Not every aggregate needs every file.

```
order/
├── Order.java                     aggregate root entity
├── OrderItem.java                 child entity
├── OrderItemOption.java           child entity
├── OrderStatus.java               enum
├── KitchenStatus.java             enum
├── OrderCode.java                 value object
├── OrderRepository.java           Spring Data interface
├── OrderService.java              transactional boundary, use cases
├── OrderController.java           HTTP endpoints
├── OrderEvents.java               event payload records
└── api/
    ├── CreateOrderRequest.java
    ├── AddItemRequest.java
    ├── ApplyDiscountRequest.java
    ├── OrderResponse.java
    ├── OrderItemResponse.java
    └── OrderSummaryResponse.java
```

| File | Role | Rules |
| --- | --- | --- |
| Entity | JPA aggregate root or child | Protected no-arg constructor, accessors named `id()` not `getId()`, no setters — state changes go through intention-revealing methods (`confirm()`, `void_(reason)`) |
| Enum | Domain state | Mirrors the SQL `CHECK` constraint exactly |
| Value object | `record` | Immutable, validates in its compact constructor (`Money`, `OrderCode`, `Quantity`) |
| Repository | Spring Data interface | No `tenantId` parameters — RLS scopes it. Custom queries use QueryDSL, not string JPQL |
| Service | Use cases | The `@Transactional` boundary. One public method per use case. No interface + `Impl` pair |
| Controller | HTTP only | No business logic, no `@Transactional`. Maps request records to service calls |
| `api/` records | Request and response DTOs | `record`s. Entities never cross the HTTP boundary. Mapping via a static `from(Entity)` factory on the response record — no MapStruct |
| Events | Outbox payloads | `record`s matching the schema in `contracts/events/` |

---

## 3. Shared per-service packages

```
config/
├── TransactionConfig.java         transaction advisor order (RLS aspect depends on it)
├── SecurityConfig.java            method security, filter chain
├── OpenApiConfig.java             springdoc metadata
├── KafkaConfig.java               from phase 3
└── GrpcConfig.java                where the service exposes or calls gRPC

outbox/
├── OutboxEntry.java
├── OutboxRepository.java
└── OutboxPublisher.java

idempotency/
├── IdempotencyRecord.java
├── IdempotencyRepository.java
└── IdempotencyService.java
```

Genuinely cross-service code goes to `libs/`, not copied per service. Currently
`libs/tenancy` and `libs/web`; `libs/money`, `libs/outbox` and `libs/testing` arrive in
later phases.

---

## 4. Resources

```
src/main/resources/
├── application.yml                committed, no secrets
├── application-local.yml          committed, local ports and dev settings
└── db/migration/
    ├── V1__identity_init.sql
    ├── V2__device_and_credentials.sql
    └── V3__staff_pin_lockout.sql
```

Migration naming: `V<n>__<snake_case_description>.sql`. One migration per logical schema
change, shipped in the same commit as the code that needs it. Migrations are never
edited after being committed — a correction is a new migration.

---

## 5. Tests

```
src/test/java/io/cloudpos/<service>/
├── ArchitectureTest.java              ArchUnit: package and dependency rules
├── TenantIsolationTest.java           build-breaking cross-tenant check
├── order/
│   ├── OrderServiceTest.java          unit, mocked repository
│   ├── OrderControllerTest.java       MockMvc slice
│   ├── OrderConcurrencyTest.java      Testcontainers, parallel writes
│   └── OrderRepositoryTest.java       Testcontainers, query behaviour
└── contract/
    └── OrderEventContractTest.java    producer side of the contract
```

Rules:

- Every service has `TenantIsolationTest` and `ArchitectureTest`. Both are non-negotiable.
- Concurrency tests run against a real PostgreSQL via Testcontainers, never H2. H2 does
  not have RLS, exclusion constraints, or the same locking behaviour, so a test that
  passes on H2 proves nothing about production.
- Test naming is a sentence: `readsOnlyOwnTenantRows`, `rejectsDoubleBookingOnSameTable`.

---

## 6. Repository-root directories

```
contracts/
├── openapi/
│   ├── identity.yaml
│   ├── catalog.yaml
│   └── order.yaml
└── events/
    ├── order/order.confirmed.v1.json
    └── inventory/stock.changed.v1.json

tools/
├── seed/                          realistic demo data generator
└── loadtest/                      k6 scripts and committed result artefacts

infra/
├── compose/
├── helm/
└── terraform/
```

---

## 7. Frontend

`apps/pos-terminal` is the reference; the two admin apps follow the same shape.

```
apps/pos-terminal/src/
├── routes/
│   └── order/
│       ├── OrderPage.tsx
│       ├── OrderPage.states.ts        empty / loading / filled / error
│       ├── MenuGrid.tsx
│       ├── CartPanel.tsx
│       └── useOrderDraft.ts
├── components/                        shared, presentational only
├── api/                               generated from OpenAPI, never hand-written
├── offline/
│   ├── queue.ts
│   ├── sync.ts
│   └── db.ts                          Dexie schema
├── stores/                            Zustand, client state only
└── theme/                             consumes packages/tokens

packages/
├── tokens/        generated from Figma: primitive → semantic CSS variables
├── ui/            design-system components (Button, Badge, Card, Modal)
├── api-client/    generated from contracts/openapi, not committed
└── types/         shared domain types
```

Rules:

- Server state lives in TanStack Query, client state in Zustand. They are never mixed.
- A route implements **all** its states from the Figma kit — empty, loading, filled,
  error, and every modal. A half-stated route looks unfinished no matter how polished
  the happy path is.
- Components consume semantic tokens only (`--button-primary-bg`), never primitives
  (`--primary-500`) and never hex values. Dark mode is then one attribute switch.

---

## 8. Naming

| Thing | Convention | Example |
| --- | --- | --- |
| Service class | `<Aggregate>Service` | `OrderService` |
| Controller | `<Aggregate>Controller` | `OrderController` |
| Request DTO | `<Verb><Noun>Request` | `CreateOrderRequest` |
| Response DTO | `<Noun>Response` | `OrderItemResponse` |
| Event record | `<Noun><PastTenseVerb>` | `OrderConfirmed` |
| Migration | `V<n>__<description>.sql` | `V2__device_and_credentials.sql` |
| Error code | `SCREAMING_SNAKE` | `INVENTORY_INSUFFICIENT` |
| Kafka topic | `cloudpos.<producer>.<aggregate>.v<n>` | `cloudpos.order.order.v1` |
| React route component | `<Name>Page.tsx` | `OrderPage.tsx` |

---

## 9. Expected file count

| Service | Files at completion |
| --- | --- |
| gateway | ~15 |
| identity | ~55 |
| catalog | ~45 |
| order | ~90 |
| inventory | ~60 |
| payment | ~55 |
| billing | ~65 |
| realtime-gateway | ~20 |
| reporting | ~35 |
| libs (all) | ~40 |
| pos-terminal | ~180 |
| admin apps | ~120 |

Roughly 800 source files at completion. The count is a consequence of the conventions
above, not a target.
