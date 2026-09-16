# ADR 0012: PortOne V2 as payment gateway; PCI scope boundary

**Status:** Accepted
**Date:** 2026-09-05

## Context

Payment must be a real integration, not a mock, and the target market is Korea. PortOne
(formerly 아임포트) aggregates Korean PGs behind one API, which makes multi-PG support a
configuration concern rather than an integration project per provider.

PortOne recommends V2 for new integrations. V2 uses hostname `api.portone.io` and a V2 API
Secret issued from the admin console, passed either directly as
`Authorization: PortOne <secret>` or as a bearer access token. PortOne's documentation
recommends a read timeout of at least 60 seconds because of downstream PG latency, and
notes that a request continues server-side even if the client disconnects — retrying with
the same idempotency key returns the outcome.

## Decision

- **PortOne V2** as the gateway, with **Toss Payments** as the first concrete PG channel.
  A **Stripe** adapter is added second, to prove the abstraction is real rather than a
  single-provider wrapper.
- A `PaymentGateway` port with one adapter per provider. Provider selection is per-tenant
  configuration, not compile-time.
- Client timeouts follow the provider's guidance: 60 s read timeout, no client-side
  cancellation, retry with the same idempotency key.
- **Webhooks**: signature verified, raw payload persisted before processing, processed
  idempotently by provider transaction ID. Duplicate and out-of-order delivery are assumed.
- **Async methods** (QR, virtual account) use an explicit state machine with an expiry and
  a server-side reconciliation sweeper. Terminals do not hold an open polling loop.
- **Daily reconciliation**: pull the provider's settlement file, compare line by line
  against internal payment records and ledger postings, write mismatches to an exceptions
  table for human review.

**PCI-DSS scope boundary.** Card data never reaches our servers. The terminal uses the
provider's hosted payment UI or SDK; we persist only a provider token and the last four
digits. The boundary is stated in `docs/security/pci-scope.md`.

## Consequences

- Payment is the slowest and least reliable dependency; the degradation ladder (ADR 0016)
  allows orders to be taken and marked unpaid when it is unavailable.
- Two adapters mean two sandbox accounts and two sets of webhook contract tests.
- The reconciliation job is the most production-realistic component in the system and is
  scoped as a first-class feature, not a script.
