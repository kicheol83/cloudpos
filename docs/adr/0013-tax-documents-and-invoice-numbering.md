# ADR 0013: Korean tax documents and gapless invoice numbering

**Status:** Accepted
**Date:** 2026-09-05

## Context

Three distinct artefacts are commonly conflated:

1. **Customer receipt** — issued per order, always, internal.
2. **현금영수증 (cash receipt)** — filed with the 국세청, issued per qualifying transaction.
3. **세금계산서 (tax invoice)** — B2B document, filed with the 국세청.

Cash receipts are asynchronous by nature: after the API call, the document goes to the
국세청 for approval and typically completes within roughly two to three days. It cannot
be treated as a synchronous step in the order flow.

PortOne V2 exposes 현금영수증 APIs. Its B2B service API, which covers 세금계산서, is at an
alpha stage, so it is not a safe dependency for that document type.

## Decision

- **현금영수증** issued through PortOne V2's cash-receipt API.
- **세금계산서** issued through **Popbill (LinkHub)**, which offers a test environment and
  covers 홈택스 filing.
- Both are modelled as a **state machine** — `REQUESTED → SUBMITTED → APPROVED | REJECTED`
  — with polling and webhook reconciliation, and a support queue for rejections. The order
  and payment flows never block on tax-document issuance.
- **VAT** is 10%. The rate is per-tenant configuration with an effective-date history, so
  a future rate change does not rewrite past invoices. Rounding is defined once in
  `billing` and unit-tested against known cases.
- **Invoice numbering is gapless per tenant per fiscal year.** PostgreSQL sequences are
  not used: a rolled-back transaction consumes a number, producing gaps. Instead a
  per-tenant counter row is taken with `FOR UPDATE` inside the issuing transaction
  (ADR 0008).
- **Issued documents are immutable.** Corrections are new documents referencing the
  original (credit note). A database trigger rejects `UPDATE` and `DELETE` on issued rows.
- PDFs are rendered once, hashed with SHA-256, stored in object storage with the hash in
  the database. Re-rendering must reproduce the same hash.
- Settlement uses an internal append-only double-entry posting table. Every order,
  payment, refund, discount and tax amount produces balanced postings. A daily job asserts
  that postings sum to zero and that closing balances match the materialised snapshot.
  A failed assertion pages; it does not warn.

## Consequences

- Invoice issuance serialises per tenant. At invoice volume this is acceptable; at
  100× volume it would need a per-tenant allocator with pre-reserved blocks.
- Tax-document state must be surfaced in the merchant admin, because rejections require
  merchant action.
- Two external providers means two sandbox integrations and two failure modes.

## Alternatives considered

- **PortOne B2B API for 세금계산서:** alpha status makes it unsuitable as the only path.
- **PostgreSQL sequence for invoice numbers:** simplest, but produces gaps, which is
  non-compliant.
- **Allowing invoice updates:** would make the audit trail meaningless.
