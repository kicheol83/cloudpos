# CloudPos — Service Contracts

Companion to `docs/domain-model.md` and `docs/adr/`. Defines the event catalog, the
synchronous API surface, and the conventions every service must follow.

**Contract-first.** The OpenAPI document and the event schema are written before the
implementation. Frontend clients and inter-service clients are generated, never
hand-written. A contract change is a reviewed pull request against this document.

---

## 0. Decisions closing the domain-model open questions

| Question | Decision |
| --- | --- |
| Discounts | Item-level and order-level. `PERCENT` or `FIXED`. Mandatory reason code. Above a per-store threshold requires a Manager PIN. Always audited. Own ledger account. |
| Loyalty | Authoritative in CloudPos. `Customer` aggregate added to `identity`. Balance is the sum of an append-only `loyalty_transaction` log, never a stored column. Redemption produces an order discount plus a ledger posting. |
| Delivery | Out of scope for v1. `DELIVERY` order type is accepted with an address and a manual courier handoff. No platform integration — stated explicitly rather than mocked. |
| Chain menus | `Dish` and `Category` are keyed on `tenant_id`. `store_dish_override` carries per-store price and availability. `recipe_line` is keyed on `(tenant_id, store_id, dish_id)` because ingredient stock is per store. |

---

## 1. Event envelope

Every Kafka message carries the same envelope. Only `payload` varies.

```json
{
  "event_id": "0192f4c1-...",
  "event_type": "order.confirmed",
  "event_version": 1,
  "occurred_at": "2026-09-05T11:32:07.412Z",
  "tenant_id": "...",
  "store_id": "...",
  "producer": "order",
  "trace_id": "...",
  "payload": { }
}
```

| Field | Rule |
| --- | --- |
| `event_id` | UUIDv7. The consumer deduplication key (ADR 0009). |
| `event_type` | `<aggregate>.<past-tense verb>`. Never a command. |
| `event_version` | Integer. Incremented only on a breaking payload change. |
| `occurred_at` | When the fact happened, not when it was published. |
| `trace_id` | Propagated from the originating HTTP request for end-to-end tracing. |

**Events are facts, not instructions.** `order.confirmed`, never `deduct_stock`. A
consumer decides what to do; the producer does not know its consumers.

---

## 2. Topic design

| Rule | Value |
| --- | --- |
| Naming | `cloudpos.<producer>.<aggregate>.v<version>` |
| Key | `<tenant_id>:<store_id>` — guarantees per-store ordering (ADR 0009) |
| Partitions | 64 initially. Repartitioning breaks key ordering, so this is sized with headroom, not grown later. |
| Retention | 7 days for domain events; compacted for state topics |
| Dead letter | `<topic>.dlq`, monitored, with a replay tool |

Ordering across stores is **not** guaranteed and no consumer may depend on it.

---

## 3. Event catalog

### order

| Event | Payload (essential fields) | Consumers |
| --- | --- | --- |
| `order.confirmed` | order_id, session_id, business_date, items[{dish_id, qty, options[]}], totals | inventory, billing, reporting, realtime |
| `order.voided` | order_id, reason_code, voided_by_staff_id | inventory, billing, reporting |
| `order.item.status_changed` | order_id, item_id, from, to | realtime, reporting |
| `order.discount_applied` | order_id, scope, type, amount, reason_code, approved_by | billing, reporting |
| `session.opened` | session_id, table_ids[], guest_count | realtime, reporting |
| `session.tables_changed` | session_id, added[], removed[] | realtime |
| `session.closed` | session_id, order_ids[], totals | billing, realtime, reporting |
| `reservation.confirmed` | reservation_id, table_ids[], period, party_size | realtime, reporting |
| `reservation.cancelled` | reservation_id, reason | realtime |
| `reservation.no_show` | reservation_id | reporting |

### inventory

| Event | Payload | Consumers |
| --- | --- | --- |
| `stock.changed` | ingredient_id, delta, on_hand_after, source_type, source_id | inventory (projection), realtime, reporting |
| `stock.low` | ingredient_id, on_hand, threshold | realtime |
| `stock.negative_detected` | ingredient_id, on_hand, source_order_id | realtime, reporting |
| `dish.availability_changed` | dish_id, can_be_served_qty | realtime, catalog cache |
| `purchase_request.created` | request_id, supplier_id, lines[] | reporting |
| `purchase_request.received` | request_id, received_lines[] | reporting |

### payment

| Event | Payload | Consumers |
| --- | --- | --- |
| `payment.captured` | payment_id, order_id, method, amount, provider_tx_id | billing, order, realtime, reporting |
| `payment.failed` | payment_id, order_id, failure_code | order, realtime |
| `payment.expired` | payment_id, order_id | order, realtime |
| `refund.completed` | refund_id, payment_id, amount, reason | billing, inventory, reporting |
| `reconciliation.exception_opened` | exception_id, kind, amounts | billing, reporting |

### billing

| Event | Payload | Consumers |
| --- | --- | --- |
| `receipt.issued` | receipt_id, order_id, document_number | reporting |
| `tax_document.state_changed` | document_id, type, from, to, reject_reason | realtime, reporting |
| `shift.settled` | shift_id, expected, counted, variance | reporting |
| `daily_close.completed` | business_date, totals | reporting |

### catalog / identity

| Event | Payload | Consumers |
| --- | --- | --- |
| `dish.upserted` | dish_id, name, category_id, is_active | inventory (replica), reporting |
| `dish.price_changed` | dish_id, store_id, amount, effective_from | reporting |
| `menu.version_bumped` | store_id, version | realtime (forces terminal refresh) |
| `staff.role_changed` | staff_id, from, to, changed_by | audit, realtime |
| `device.revoked` | device_id | realtime (force disconnect) |
| `tenant.suspended` | tenant_id, reason | gateway (reject writes) |

---

## 4. Saga commands

Sagas are orchestrated (ADR 0010), so these are **commands**, not events, and are sent
point-to-point over gRPC with a saga correlation ID.

### Order settlement saga

```
order.CloseSession
  → payment.RequestPayment          ⟲ payment.CancelPayment
  → billing.IssueReceipt            ⟲ billing.IssueCreditNote
  → billing.RequestTaxDocument      (fire-and-forget, own state machine)
  → order.MarkSettled
```

### Void and refund saga

```
order.RequestVoid
  → payment.RequestRefund           ⟲ (no compensation — refunds are terminal)
  → inventory.RestoreStock          ⟲ inventory.DeductStock
  → billing.IssueCreditNote
  → order.MarkVoided
```

Every command is idempotent on `(saga_id, step)`. Every compensation is a business-level
reversal, never a rollback.

---

## 5. Synchronous API conventions

### Base

| Concern | Rule |
| --- | --- |
| Style | REST over HTTPS for public APIs, gRPC for inter-service calls where latency matters |
| Versioning | URL prefix `/v1`. A breaking change means `/v2`, run in parallel, old version deprecated with a sunset header |
| Casing | `snake_case` in JSON bodies |
| Time | RFC 3339 with offset, always |
| Money | `{"amount": 12500, "currency": "KRW"}` — integer minor units, never a decimal string |
| Pagination | Cursor-based: `?cursor=&limit=`. Offset pagination is not offered — it breaks on high-write tables |
| Filtering | Explicit named parameters. No generic query DSL in the public API |

### Required headers

| Header | Applies to | Purpose |
| --- | --- | --- |
| `Authorization: Bearer <jwt>` | all | Staff access token |
| `X-Device-Id` | terminal | Device identity; must match the token's device claim |
| `Idempotency-Key` | all mutations | ADR 0011 |
| `X-Menu-Version` | order submission | Stale terminal detection |
| `traceparent` | all | W3C trace context |

### Error model

RFC 9457 Problem Details.

```json
{
  "type": "https://docs.cloudpos.io/errors/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "code": "INVENTORY_INSUFFICIENT",
  "detail": "Dish cannot be fulfilled with current stock",
  "instance": "/v1/orders/0192f4c1",
  "errors": [
    { "ingredient_id": "...", "required": 250, "available": 120, "unit": "G" }
  ],
  "trace_id": "..."
}
```

`code` is a stable machine-readable enum. Clients branch on `code`, never on `title` or
`detail`, which are free to change.

| Status | Used for |
| --- | --- |
| 409 | Business invariant violated (stock, table occupied, reservation overlap) |
| 422 | Idempotency key reused with a different request fingerprint |
| 423 | Terminal menu version stale; refresh required |
| 428 | Manager approval required (discount above threshold, void) |

---

## 6. The offline sync contract

The most important endpoint in the system (ADR 0011).

```
POST /v1/sync/orders
```

```json
{
  "device_id": "...",
  "menu_version": 418,
  "orders": [
    {
      "client_order_id": "0192f4c1-...",
      "created_at_local": "2026-09-05T11:02:44+09:00",
      "order_type": "DINE_IN",
      "session_ref": { "client_session_id": "..." },
      "items": [ { "client_item_id": "...", "dish_id": "...", "quantity": 2,
                   "unit_amount": 12500, "note": "no onion",
                   "options": [ { "option_id": "..." } ] } ],
      "payments": [ { "method": "CASH", "tendered_amount": 30000 } ]
    }
  ]
}
```

**Response is per-order, never all-or-nothing:**

```json
{
  "results": [
    { "client_order_id": "...", "status": "ACCEPTED", "order_id": "...",
      "warnings": [ { "code": "STOCK_WENT_NEGATIVE", "ingredient_id": "..." } ] },
    { "client_order_id": "...", "status": "DUPLICATE", "order_id": "..." },
    { "client_order_id": "...", "status": "REJECTED",
      "error": { "code": "MENU_VERSION_STALE" } }
  ]
}
```

Rules:

1. **Per-order outcomes.** One bad order must not block a queue of fifty good ones.
2. **`DUPLICATE` is a success.** It returns the existing `order_id` so the terminal can
   clear its queue.
3. **Insufficient stock is a warning, not a rejection.** The sale physically happened;
   the system records negative stock and raises an alert.
4. **Stale menu version rejects the whole batch.** Prices must not be wrong.
5. **Only `CASH` payments may arrive through sync.** Card and QR require connectivity.
6. Maximum 200 orders per batch; the terminal chunks larger queues.

---

## 7. Authentication contract

**Token exchange:** device credential + staff PIN → access token (15 min) + refresh token
(rotating, reuse-detected).

Access token claims:

```json
{
  "sub": "<staff_id>",
  "tid": "<tenant_id>",
  "sid": "<store_id>",
  "did": "<device_id>",
  "role": "WAITER",
  "shift": "<shift_id>",
  "exp": 1757068800
}
```

- The gateway verifies the signature and injects `tid`/`sid` downstream. Services never
  trust a tenant ID from a request body.
- `did` must match the `X-Device-Id` header, or the request is rejected. A stolen token is
  useless on another device.
- `shift` being absent means the staff member has no open shift; sales endpoints reject
  the request.

---

## 8. Versioning and compatibility policy

| Change | Allowed without a version bump |
| --- | --- |
| Adding an optional response field | Yes |
| Adding an optional request field | Yes |
| Adding an enum value | Yes — consumers must ignore unknown values |
| Making an optional request field required | No |
| Removing or renaming a field | No |
| Changing a field's type | No |
| Changing the meaning of an existing value | No — this is the dangerous one, because it passes every schema check |

Consumers are written tolerant: unknown fields ignored, unknown enum values routed to a
default branch, never a crash.

**Contract tests.** Every producer/consumer pair has a contract test (Spring Cloud
Contract) running in CI. A producer cannot merge a change that breaks a registered
consumer. This is what makes the eight-service split safe to evolve.

---

## 9. What a new service must provide

A checklist, so that service number nine does not invent its own conventions:

1. OpenAPI document in `contracts/openapi/<service>.yaml`, generated clients published.
2. Event schemas in `contracts/events/<service>/`, versioned.
3. Registered contract tests with every consumer.
4. Health, readiness and liveness endpoints.
5. OpenTelemetry tracing with `trace_id` propagation.
6. RLS enabled on every tenant table, plus the cross-tenant isolation test suite.
7. An outbox table and publisher.
8. A documented SLO and a declared behaviour when each of its dependencies is down.
