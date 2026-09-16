# CloudPos — Domain Model

Companion to `docs/adr/`. Defines aggregate boundaries, invariants and schema shape for
each service before any code is written.

Related: ADR 0003 (service boundaries), ADR 0004 (tenancy), ADR 0006 (money and time),
ADR 0007 (partitioning), ADR 0008 (concurrency).

---

## 0. Conventions

These apply to every table in every service.

| Rule | Detail |
| --- | --- |
| Primary key | `(tenant_id, id)` composite. `id` is UUIDv7 — time-ordered, index-friendly, safe to generate on an offline terminal. |
| Tenancy | `tenant_id UUID NOT NULL` on every tenant-owned table, leading column of every index (ADR 0004). RLS enabled. |
| Money | `*_amount BIGINT` in minor units + `currency CHAR(3)`. Never `NUMERIC`, never floating point (ADR 0006). |
| Business date | `business_date DATE NOT NULL` on every transactional table, computed from store timezone + cutoff. Partition key (ADR 0007). |
| Audit columns | `created_at`, `updated_at` as `timestamptz`; `created_by_staff_id` on operator-initiated rows. |
| Optimistic locking | `version BIGINT` only where ADR 0008 specifies it. Not everywhere. |
| Enums | `VARCHAR` + `CHECK` constraint, not PostgreSQL `ENUM`. Adding a value must not require a table rewrite. |
| Deletion | Business records are never deleted. Configuration entities use `deleted_at`. |
| Cross-service references | By ID only. No foreign keys across service databases (ADR 0005). Locally replicated fields are marked `-- replica`. |

**Cross-aggregate rule.** Inside a service, one transaction modifies one aggregate. A
change spanning two aggregates goes through an event, or is an explicit exception with a
written justification.

---

## 1. identity

Owns tenants, stores, staff, devices and work shifts.

### Aggregates

| Aggregate root | Contains | Invariants |
| --- | --- | --- |
| `Tenant` | plan, status, billing contact | A tenant has at least one store. Suspended tenants reject all writes at the gateway. |
| `Store` | floors, timezone, business-day cutoff, tax profile | Cutoff is immutable while an open shift exists. |
| `Staff` | role assignments, PIN credential | A store has at least one staff with the Owner or Manager role. |
| `Device` | registration, credential, last seen | A device belongs to exactly one store. Revoking it invalidates all its tokens. |
| `Shift` | open/close times, staff, opening float | A staff member has at most one open shift per store. A shift cannot close with unsynced offline orders on its device. |

### Notes

- **Authentication is device + PIN, not PIN alone** (ADR 0016 security section). A PIN is
  a store-scoped secret; the device credential is what makes it a credential.
- **`Shift` lives here, `ShiftSettlement` lives in `billing`.** The work session is an
  identity concern; the cash reconciliation that closes it is a financial record that must
  be immutable and auditable. The split is deliberate.

### Key schema

```sql
staff (
  tenant_id, id,
  store_id, employee_code, display_name,
  role,                    -- OWNER | MANAGER | WAITER | KITCHEN
  employment_status,       -- ACTIVE | SUSPENDED | TERMINATED
  pin_hash,                -- argon2id
  pin_failed_count, pin_locked_until,
  joined_on, deleted_at, created_at, updated_at
)

shift (
  tenant_id, id,
  store_id, staff_id, device_id,
  business_date,
  opened_at, closed_at,
  opening_float_amount, currency,
  status,                  -- OPEN | CLOSING | CLOSED
  version
)
-- partial unique index: one open shift per (tenant, store, staff)
```

---

## 2. catalog

Owns the menu. Read-heavy, near-static, cached aggressively.

### Aggregates

| Aggregate root | Contains | Invariants |
| --- | --- | --- |
| `Category` | display order, icon | Name unique per store. |
| `Dish` | option groups, option items, price history | A dish belongs to exactly one category. Exactly one price row is effective at any instant. |
| `MenuVersion` | version stamp for terminal cache | Incremented on any dish, option or price change. |

### The price history decision

Prices change. Yesterday's receipt must not change with them.

```sql
dish_price (
  tenant_id, id,
  dish_id,
  base_amount, currency,
  effective_from timestamptz NOT NULL,
  effective_to   timestamptz,        -- NULL = current
  created_by_staff_id, created_at
)
-- EXCLUDE constraint prevents overlapping effective periods per dish
```

The order service reads the effective price at order time and **snapshots it** into the
order line (§3). It never joins back to catalog for a historical price.

### Terminal cache contract

`MenuVersion` is a monotonic counter per store. Terminals send their cached version on
every order submission; a stale version is rejected and forces a refresh before the
terminal may submit (ADR 0011). This is what prevents an offline terminal from selling at
last month's prices.

---

## 3. order

The largest service. Owns tables, floors, sessions, orders, kitchen status and
reservations — all bound by the table-availability invariant (ADR 0003).

### Aggregates

| Aggregate root | Contains | Invariants |
| --- | --- | --- |
| `FloorPlan` | floor, table definitions, layout coordinates | Table number unique per store. Layout edits are blocked while any session is open on an affected table. |
| `TableSession` | occupied table IDs, guest count, open/close | A table belongs to at most one open session. A session has at least one table. |
| `Order` | order items, options, notes, totals | Totals equal the sum of lines. A confirmed order cannot change price. Dine-in orders reference an open session. |
| `Reservation` | pre-ordered items, assigned tables, time range | No two reservations overlap on the same table. |

### Why `TableSession` exists

The design requires three behaviours that break a naive `Order → Table` link:

1. **Multiple orders on one table.** Guests order more, later. Each is a separate `Order`
   with its own kitchen lifecycle, but one bill.
2. **Joined tables.** Two or three tables serve one party.
3. **Changing table.** The party moves; orders and kitchen state must follow.

`TableSession` is the occupancy. Orders attach to the session, not the table. Changing
table mutates the session's table set — one small write, no order rewriting.

### Table status is derived, never stored

```
status(table, now) =
    OCCUPIED     if an open TableSession includes it
    RESERVED     if a confirmed Reservation covers [now, now + lead_time)
    UNAVAILABLE  if the table definition is disabled
    AVAILABLE    otherwise
```

A stored `status` column drifts the first time an order is voided or a reservation
expires. The floor screen reads a **projection** (`table_status_view`) maintained from
session and reservation events and refreshed over WebSocket; the authoritative check runs
inside the session-opening transaction.

### Kitchen status is per line, not per order

The design shows each dish with its own state. Order status is derived from its lines:

```
order.kitchen_state = min(item.kitchen_status)   -- ordered enum
```

`WAITING → IN_PROGRESS → READY → SERVED`, with `VOIDED` as a terminal side state
requiring a manager PIN and writing an audit record.

### Key schema

```sql
table_definition (
  tenant_id, id,
  store_id, floor_id,
  table_number, capacity, shape,     -- SMALL | LARGE_H | LARGE_V
  has_baby_chair boolean,
  layout_x, layout_y,
  is_enabled boolean, deleted_at, version
)

table_session (
  tenant_id, id,
  store_id, business_date,
  guest_count,
  opened_at, closed_at,
  opened_by_staff_id,
  status,                            -- OPEN | CLOSING | CLOSED
  version
)

table_session_member (
  tenant_id, session_id, table_id,
  joined_at, left_at
)
-- partial unique index on (tenant_id, table_id) where left_at IS NULL
-- → enforces "one open session per table" in the database

"order" (
  tenant_id, id,
  store_id, business_date,
  client_order_id UUID NOT NULL,     -- terminal-generated, offline replay key
  order_code,                        -- DI104 / TA087 / RV012, display only
  order_type,                        -- DINE_IN | TAKE_AWAY | DELIVERY
  session_id,                        -- NULL for take-away
  reservation_id,                    -- NULL unless created from a reservation
  status,                            -- DRAFT | CONFIRMED | VOIDED
  subtotal_amount, discount_amount, tax_amount, total_amount, currency,
  menu_version_at_order,
  placed_by_staff_id, device_id,
  created_at, confirmed_at, version
)
-- UNIQUE (tenant_id, client_order_id)  → idempotent offline replay (ADR 0011)
-- PARTITION BY RANGE (business_date), monthly (ADR 0007)

order_item (
  tenant_id, id,
  order_id, business_date,
  dish_id,                           -- catalog reference
  dish_name_snapshot,                -- price and name frozen at order time
  unit_amount, quantity, line_amount, currency,
  note text,
  kitchen_status,                    -- WAITING | IN_PROGRESS | READY | SERVED | VOIDED
  kitchen_updated_at,
  created_at
)

order_item_option (
  tenant_id, id, order_item_id,
  option_id, option_name_snapshot, option_amount
)

reservation (
  tenant_id, id,
  store_id, business_date,
  customer_name, customer_phone_encrypted,   -- PIPA: column-level encryption
  party_size,
  period tstzrange NOT NULL,
  status,                            -- PENDING | CONFIRMED | SEATED | NO_SHOW | CANCELLED
  session_id,                        -- set when seated
  created_at, version
)

reservation_table (
  tenant_id, reservation_id, table_id, period tstzrange,
  EXCLUDE USING gist (
    tenant_id WITH =, table_id WITH =, period WITH &&
  ) WHERE (status IN ('PENDING','CONFIRMED'))
)
-- the database enforces no-double-booking (ADR 0008)
```

### Order state machine

```
DRAFT ──confirm──> CONFIRMED ──(payment succeeded)──> settled via session close
  │                    │
  └──discard──> ✕      └──void (manager PIN, audited)──> VOIDED
```

An order is confirmed **before** payment. Payment failure does not void an order — the
order stays confirmed and unpaid, which is what lets the terminal keep working when
`payment` is down (ADR 0016 degradation ladder).

---

## 4. inventory

Highest write volume, highest contention (ADR 0008).

### Aggregates

| Aggregate root | Contains | Invariants |
| --- | --- | --- |
| `Ingredient` | stock balance, unit, supplier link | Balance equals the sum of its movements. Verified daily; divergence pages. |
| `Recipe` | recipe lines for one dish | Every line references an existing ingredient. Quantities are positive. |
| `PurchaseRequest` | requested lines, status | A request cannot be received twice. |

`DishAvailability` is **not an aggregate** — it is a projection (§ below).

### Cross-service replication

`Recipe` references `dish_id` from `catalog`. There is no foreign key. Inventory keeps a
local replica of `(dish_id, dish_name, is_active)` maintained from `catalog.dish.*`
events. Replica columns are marked and are never written by inventory itself.

### Key schema

```sql
ingredient (
  tenant_id, id,
  store_id,
  name, category,                    -- FRESH_PRODUCE | MEAT_POULTRY | SEAFOOD | DAIRY_EGGS | DRY_GOODS
  unit,                              -- G | KG | ML | L | EA
  supplier_id,
  low_threshold_qty, reorder_qty,
  is_active, deleted_at, version
)

ingredient_stock (
  tenant_id, ingredient_id PRIMARY KEY,
  on_hand_qty NUMERIC(14,3) NOT NULL,   -- physical quantity, not money
  updated_at
)
-- locked with SELECT ... FOR UPDATE in ascending ingredient_id order (ADR 0008)

stock_movement (
  tenant_id, id,
  ingredient_id, business_date,
  movement_type,                     -- SALE | WASTE | ADJUSTMENT | RECEIPT | RETURN
  quantity NUMERIC(14,3),            -- signed
  source_type, source_id,            -- ORDER / PURCHASE_REQUEST / MANUAL
  staff_id, created_at
)
-- PARTITION BY RANGE (business_date), weekly; 90-day retention (ADR 0007)

recipe_line (
  tenant_id, id,
  dish_id,                           -- replica reference to catalog
  ingredient_id,
  quantity NUMERIC(14,3), unit,
  is_optional boolean                -- add-ons that consume stock
)
```

### `can_be_served` projection

```
can_be_served(dish) = floor( min over recipe lines of
                             ingredient_stock.on_hand_qty / recipe_line.quantity )
```

Maintained asynchronously from `stock.changed` events, stored in
`dish_availability (tenant_id, dish_id, can_be_served_qty, computed_at)`.

**It is advisory.** The terminal uses it to grey out dishes. The authoritative check runs
inside the order-confirmation transaction under row locks. Two answers to "is this
available?" exist by design and the API contract says so (ADR 0008).

### Write path

One order produces **one batch insert** into `stock_movement` and **one UPDATE per
distinct ingredient**, with ingredients locked in ascending ID order across the whole
order. This is the difference between ~4,000 individual writes/sec and a manageable load.

Negative `on_hand_qty` is a **legal state**, produced by offline replay when stock ran out
while disconnected (ADR 0011). Every aggregation and display path must handle it.

---

## 5. payment

### Aggregates

| Aggregate root | Contains | Invariants |
| --- | --- | --- |
| `Payment` | attempts, provider references | The sum of captured payments never exceeds the order total. |
| `Refund` | reason, provider reference | Total refunded never exceeds total captured. |
| `WebhookEvent` | raw payload, processing state | Processed at most once per provider transaction ID. |
| `ReconciliationRun` | matched lines, exceptions | A run is immutable once completed. |

### Key schema

```sql
payment (
  tenant_id, id,
  store_id, business_date,
  order_id, session_id,
  method,                            -- CASH | CARD | QR | VIRTUAL_ACCOUNT | EASY_PAY
  status,                            -- PENDING | AUTHORIZED | CAPTURED | FAILED | EXPIRED | CANCELLED
  requested_amount, captured_amount, currency,
  tendered_amount, change_amount,    -- cash only
  provider,                          -- PORTONE | STRIPE
  provider_payment_id, provider_tx_id,
  idempotency_key UNIQUE,
  card_last4, card_issuer,           -- no PAN, ever (ADR 0012 PCI scope)
  expires_at,                        -- QR / virtual account
  created_at, captured_at, version
)

webhook_event (
  tenant_id, id,
  provider, provider_event_id UNIQUE,
  raw_payload jsonb NOT NULL,        -- stored before processing
  signature_verified boolean,
  status,                            -- RECEIVED | PROCESSED | FAILED | IGNORED
  received_at, processed_at
)

reconciliation_exception (
  tenant_id, id,
  business_date, run_id,
  kind,                              -- MISSING_LOCAL | MISSING_REMOTE | AMOUNT_MISMATCH | STATUS_MISMATCH
  provider_tx_id, payment_id,
  local_amount, remote_amount,
  status,                            -- OPEN | RESOLVED | ACCEPTED
  resolved_by, resolution_note
)
```

`reconciliation_exception` is the operational heart of the payment service. Daily,
every provider settlement line is matched against a local payment and a ledger posting;
anything unmatched lands here for human review.

---

## 6. billing

Append-only, audited. Owns receipts, tax documents, the internal ledger, shift
settlement, and SaaS subscription billing.

Two sub-modules with different lifecycles, kept separate inside the service:
**merchant billing** (what the restaurant issues to its guests) and **platform billing**
(what CloudPos charges the restaurant). They share nothing but the tenant ID.

### Aggregates

| Aggregate root | Invariants |
| --- | --- |
| `Receipt` | One per settled order or session. Immutable once issued. |
| `TaxDocument` | Cash receipt or tax invoice. Immutable. Numbered gaplessly per tenant per fiscal year. |
| `LedgerEntry` | Its postings sum to zero. Append-only. |
| `ShiftSettlement` | Counted cash minus expected cash equals the recorded variance. Immutable once closed. |
| `Subscription` | A tenant has at most one active subscription. |

### Gapless numbering

```sql
invoice_counter (
  tenant_id, fiscal_year, document_type,
  next_number BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, fiscal_year, document_type)
)
```

Taken with `SELECT ... FOR UPDATE` inside the issuing transaction. PostgreSQL sequences
are not used: a rollback consumes a number and produces a gap, which is non-compliant
(ADR 0013).

### Double-entry ledger

```sql
ledger_entry (
  tenant_id, id,
  store_id, business_date,
  source_type, source_id,            -- ORDER | PAYMENT | REFUND | SETTLEMENT
  description, created_at
)

ledger_posting (
  tenant_id, id,
  entry_id, account_code,            -- SALES | VAT_PAYABLE | CASH | CARD_RECEIVABLE | DISCOUNT | ROUNDING
  amount BIGINT NOT NULL,            -- signed; sum per entry must be zero
  currency
)
```

A deferred constraint trigger rejects any entry whose postings do not sum to zero. A
daily job asserts that cumulative balances match the materialised monthly snapshot; a
failed assertion pages (ADR 0013).

### Tax document state machine

```
REQUESTED → SUBMITTED → APPROVED
                     ↘ REJECTED → (manual correction) → REQUESTED
```

Cash receipts take roughly two to three days to complete at the 국세청, so this state
machine runs entirely outside the order flow. Nothing in the terminal ever waits on it.

---

## 7. realtime-gateway

No aggregates. Stateless fan-out (ADR 0014). Holds only ephemeral subscription state in
Redis.

Channels, all scoped `tenant/store`:

| Channel | Producer | Payload |
| --- | --- | --- |
| `kitchen.item.status` | order | order item ID, new status |
| `table.status` | order | table ID, derived status, session ID |
| `order.created` | order | order summary |
| `stock.low` | inventory | ingredient ID, on-hand, threshold |
| `payment.settled` | payment | order ID, status |

---

## 8. reporting

No aggregates and no write model. ClickHouse tables populated from Kafka (ADR 0015).

| Table | Grain |
| --- | --- |
| `fact_order_item` | one row per order line |
| `fact_payment` | one row per payment |
| `agg_sales_hourly` | tenant, store, business_date, hour |
| `agg_dish_daily` | tenant, store, business_date, dish |
| `agg_ingredient_daily` | tenant, store, business_date, ingredient |

All ordered by `(tenant_id, store_id, business_date, ...)`, matching the tenancy and time
model. Financial authority remains with `billing`; reporting is operational insight only,
and the UI labels its freshness.

---

## 9. Consistency summary

| Invariant | Enforcement | Boundary |
| --- | --- | --- |
| One open session per table | Partial unique index | Transactional |
| No overlapping reservations | `EXCLUDE` constraint | Transactional |
| Order total = sum of lines | Aggregate invariant | Transactional |
| Stock never oversold while online | Row locks in order-confirmation transaction | Transactional |
| Ledger postings sum to zero | Deferred constraint trigger | Transactional |
| Invoice numbers gapless | Counter row under `FOR UPDATE` | Transactional |
| `can_be_served` accuracy | Projection from events | Eventual, seconds |
| Table status on floor screen | Projection from events | Eventual, seconds |
| Reporting totals | Kafka → ClickHouse | Eventual, minutes |
| Tax document filed | External state machine | Eventual, days |

**The rule, restated:** money and stock are never eventually consistent within a single
business operation. Everything else may be.

---

## 10. Open questions before schema freeze

1. **Discounts.** The design shows a discount line but not its rules. Per-item or
   per-order? Percentage or fixed? Who may apply one, and is it audited? This affects
   `order` and the ledger account map.
2. **Loyalty points.** Accrual rate, redemption unit, and whether the balance is
   authoritative in CloudPos or in an external member system. Currently unmodelled.
3. **Delivery orders.** `order_type` includes `DELIVERY`, but no platform integration is
   designed. Scope it out explicitly or model the courier handoff.
4. **Multi-store chains.** Does a tenant with 20 branches share one menu, or one per
   store? This changes whether `catalog` keys on store or tenant.
