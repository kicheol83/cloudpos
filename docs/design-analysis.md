# CloudPos UI Kit — Design Analysis

Source file: `CloudPos - Point of Sale UI KIT.fig` (AJP Design, 2024–2025)
Decoded from fig-kiwi + zstd: 31,697 nodes, 13 pages, 91 unique screens.

> Design credit: the visual design is the **CloudPos Point of Sale UI Kit by AJP Design**.
> Implementation, architecture, and all backend work in this repository are original.

---

## 1. Product shape

| Property | Value |
| --- | --- |
| Domain | Restaurant / F&B point of sale (not retail) |
| Target device | iPad landscape, 1194 × 834 |
| Themes | Light + Dark, fully tokenised |
| Screens | 91 unique, mirrored across both themes (182 frames) |
| Component library | 47 component groups, 1,539 symbols |
| Icon set | Tabler Icons |
| Type | Open Sans |

The kit covers the **terminal / staff-facing app only**. There is no merchant admin
console, tenant onboarding, or back-office reporting design. Those must be designed
separately or scoped out.

---

## 2. Screen inventory

### 1 — Authentication (2)
- Select Employee (PIN-based employee login)

### 2 — Forgot PIN (2)
- Home
- Check Email

### 3 — Dashboard (3)
- Filled
- Notification Expand
- Empty

### 4 — Order (3)
- iPad View
- Sorting
- Detail Order

### 5 — Create New Order / Dine In (9)
- Select Table
- Table Selected
- Dine In Selected
- Menu Empty
- Menu Filled
- Add Order
- Select Order
- Customer Informations
- Success Order

### 5 — Create New Order / Take Away (9)
- Menu Empty
- Menu Filled
- Add Order
- Select Order
- Take Away Selected
- Customer Informations
- Pay
- Success Order
- Success Payment

### 6 — Table (18)
- Home
- Table Selected
- Reservation Information
- Reservation Details
- Detail Table / Food In Progress
- Detail Table / Food All Served
- Detail Table / Change Table
- Table Setting / Dropdown
- Table Setting / Add New Table / Table Info 1–3
- Table Setting / Add New Table / Layout Arrange 1–3
- Table Setting / Add New Table / Success
- Table Setting / Edit Table / Table Info
- Table Setting / Edit Table / Layout Arrange
- Table Setting / If Floor 4+

### 7 — Reservation (11)
- Home
- Add New / Date and Time Reservation
- Add New / Date and Time Selected
- Add New / Fill Customer Information
- Add New / Customer Information Filled
- Add New / Select Table
- Add New / Select Table – Selected
- Add New / Add Dishes
- Add New / Add Dishes – Filled
- Add New / Reservation Summary
- Add New / Reservation Created

### 8 — Add New Order (3)
- New Order Appear
- Pop Up Add New Order
- Detail Table

### 9 — Payment (9)
- Select Table or Card Order
- Cash / Pay
- Cash / Success Payment
- Card / Pay
- Card / Move to EDC Machine
- Card / Success Payment
- QR Code / Pay
- QR Code / Process
- QR Code / Success Payment

### 10 — Account Setting (9)
- Profile
- Account
- Notification
- Change PIN
- Success Change PIN
- Change Language
- Display
- Log Out Shift End
- Confirm Log Out

### 11 — Order History (2)
- Bill Selected (two variants)

### 12 — Inventory (11)
- Menu List / Home
- Menu List / Detail Menu
- Menu List / Add New Dish 1–2
- Menu List / Success Add New Dish
- Ingredients / Home
- Ingredients / Request
- Ingredients / More Option
- Ingredients / Add New Ingredients – Default
- Ingredients / Add New Ingredients – Filled
- Ingredients / Success Add New Ingredients

---

## 3. Domain vocabulary extracted from the design

### Order
- Types: `Dine In`, `Take Away`, `Online Delivery`, `Delivery`
- Identifiers: `OD###` (order), `DI###` (dine in), `TA###` (take away), `RV###` (reservation)
- Line item extras: `Add On 1–4`, free-text `Note:` (e.g. "Don't use onion"), `Qty`
- Totals: `Sub Total`, `Discount`, `Tax 12%`, `Total Payment`

### Dish status (kitchen flow)
`Waiting to cooked` → `In Progress` → `Can be served` → `Served`

Notifications: `Kitchen Update`, `Dish Ready to Serve!`, `Low Stock Alert!`

### Table
- States: `Available`, `Not Available`, `Reserved`, `Selected`, `Can't Select`
- Attributes: `Table Number`, `Capacity`, `Floor #`, `Floor Type` (Indoor / Outdoor),
  `Baby Chair`
- Types: `Small Table`, `Large Table (H)`, `Large Table (V)`
- Operations: layout drag & drop arrange, `Change Table`, join tables (2 or 3),
  `Remaining Small Table` / `Remaining Large Table` counters

### Reservation
- `Reservation Date`, `Reservation Time`, `How many people` (`1-4 People` … `6+ People`)
- Customer information, table assignment, optional pre-ordered dishes

### Inventory
- Two sub-domains: **Menu List** (dishes) and **Ingredients**
- Dish: name, category (`Rice`, `Noodle`, `Soup`, `Dessert`, `Drink`, `Vegetable`),
  photo, `Base Price`, description, `Chef Recommendation`, ingredient list with
  quantities ("3 cloves (minced)", "2 tablespoons", "1 inch (julienned)")
- Ingredient: name, photo, `Supplier`, `Stock Level` (`low` / `High`),
  `Unit Measurement` (`g`, `kg`, `ml`), `Out of Stock`
- Derived field: **`Can be served: N`** — portions available, computed from the recipe
  against current ingredient stock

### Payment
- Methods: Cash, Credit Card (EDC terminal), QR Code, Virtual Account / bank transfer,
  e-wallet
- Cash flow: `Customer Pays` → change calculation
- QR flow: `Complete payment in <countdown>` → `Checking Payment` → success/expiry
- Card flow: `Tap or Swipe card at EDC Machine`
- Loyalty: `Member Points`, `Member Code#`, `100 points = US$ 1`

### Employee / shift
- PIN login, `Employee ID#`, `Access Role` (Manager, Waiter), `Employment Status`,
  `Joining Date`
- `Your shift today`, `Total Earning`, `Total Sales`, `Log Out Shift End`

---

## 4. Design tokens

### Primitive palette

| Ramp | 500 | Notes |
| --- | --- | --- |
| Primary | `#447DFC` | 25–950, blue brand |
| Success | `#10B981` | 25–950 |
| Warning | `#F59E0B` | 25–950 |
| Error | `#EF4444` | 25–950 |
| Gray Light | `#64748B` | slate-like, light theme surfaces |
| Gray Dark | `#70707B` | zinc-like, dark theme surfaces |
| Alpha Black | `#131316` | 0–100 opacity steps |
| Alpha White | `#FFFFFF` | 0–100 opacity steps |

Primary ramp: 25 `#F9FCFF`, 50 `#F0F6FE`, 100 `#DEEAFC`, 200 `#C4DAFC`, 300 `#9AC4FE`,
400 `#65A0FD`, 500 `#447DFC`, 600 `#2D5CF2`, 700 `#2346DD`, 800 `#223BB1`,
900 `#233889`, 950 `#192452`.

### Semantic layer (`App Color`, light + dark modes)

Every semantic token aliases a primitive, per mode. Groups:

`Button/{Primary,Secondary,Outline,Pil,Disabled}` ·
`Badge/{Primary,Gray,Blue,Green,Yellow,Red}/{Background,Text,Icon,InnerCircle,OuterCircle}` ·
`Input Field/{Default,Disabled,Error,Verification Code}` ·
`Card/{Background,Outline,Icon,Icon Background}` ·
`Table/{Available,Not Available,Reserved,Selected,Can't Select}` ·
`Text/{Primary,Secondary,Caption,Color}` · `Icon/*` · `Divider/{Light,Dark}/{Regular,Medium,Bold}` ·
`Frame/Background/*` · `Calendar/*` · `Progress Bar/*` · `Selection/*` · `Banner/*` ·
`Shadow Color/{First,Second}` · `Skeumorphic Effect/*`

Implementation note: mirror this two-layer structure exactly. Primitives become
`--primary-500` etc.; semantic tokens become `--button-primary-bg` etc. and are the
only thing components reference. Dark mode then costs one attribute switch.

### Typography — Open Sans

| Token | Size | Line height |
| --- | --- | --- |
| heading-1 | 72 | 90 |
| heading-2 | 60 | 72 |
| heading-3 | 48 | 60 |
| heading-4 | 36 | 44 |
| heading-5 | 30 | 38 |
| heading-6 | 24 | 32 |
| text-xl | 20 | 30 |
| text-lg | 18 | 28 |
| text-md | 16 | 24 |
| text-sm | 14 | 20 |
| text-xs | 12 | 18 |

Weights: Regular, Medium, Semi Bold, Bold.

---

## 5. Backend bounded contexts implied by the design

| Context | Responsibility | Key technical problem |
| --- | --- | --- |
| Tenant & Store | store, floors, settings, tax rate | tenant isolation (RLS) |
| Staff & Shift | PIN auth, roles, shift open/close, earnings | short-credential auth, shift settlement |
| Floor & Table | layout, table state, join/move | realtime state across terminals |
| Catalog | dishes, categories, add-ons, pricing | option/modifier pricing |
| Inventory | ingredients, suppliers, stock movements, recipes | BOM deduction, `can_be_served` derivation, lock ordering |
| Order | cart, line items, notes, lifecycle | offline creation + idempotent replay |
| Kitchen | dish-level status transitions | realtime push, ordering guarantees |
| Reservation | slots, table assignment, pre-orders | double-booking prevention |
| Payment | cash/card/QR/VA, refunds | idempotency keys, async polling, expiry |
| Settlement | shift close, daily sales, tax | outbox-driven projections |
| Loyalty | member points, accrual, redemption | balance consistency |

---

## 6. Gaps to design yourself

1. **Offline states.** No sync indicator, pending-queue view, or conflict resolution
   screen exists in the kit. Build from existing Banner / Badge / Pop Up components.
2. **Merchant admin console.** No design. Either build a minimal one or scope
   multi-tenancy as a backend-only concern.
3. **Kitchen display (KDS).** Dish statuses exist but there is no dedicated kitchen
   screen; the flow is driven from the waiter's terminal.
4. **Localisation.** Currency is IDR / US$ and tax is 12%. For a Korea-targeted
   portfolio, switch to KRW (zero decimal places), 부가세 10%, `Asia/Seoul`, and a
   business-day cutoff.
