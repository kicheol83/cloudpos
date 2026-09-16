# ADR 0020: Transactional methods that record a failure must not throw

**Status:** Accepted
**Date:** 2026-09-17

## Context

Two security-relevant defects appeared during phase 1, both from the same cause.

**Failed PIN attempts.** `verifyPin` incremented `staff.pin_failed_count` and then threw
on a wrong PIN. The throw propagated out of the `@Transactional` boundary, Spring rolled
the transaction back, and the increment was discarded. Lockout could never engage, and
PINs were brute-forceable without limit.

**Refresh token reuse.** `rotate` detected a replayed token, called `revokeFamily` to
revoke every token in that family, and then threw. Same rollback, same result: the
revocation was lost and the stolen token family stayed valid. A test written for the
expected behaviour caught it; without that test it would have shipped.

The pattern is general. Any operation that **records something and then reports
failure** is affected: the record is part of the same transaction as the failure, and
Spring's default rollback-on-`RuntimeException` deletes it.

## Decision

**A transactional method that must persist a fact about a failure does not throw. It
returns an outcome, and the caller — outside the transaction — throws.**

Concretely:

- The service method is `@Transactional` and returns a result type carrying an outcome
  enum (`PinVerification`, `RotationOutcome`).
- The mutation is applied and the transaction commits normally.
- A non-transactional caller — a handler or another service — maps the outcome to an
  `ApiException`.

`REQUIRES_NEW` on a separate bean is the alternative and is rejected as the default: it
opens a second connection while holding the first, doubles pool pressure on the hottest
paths, and hides the control flow. It remains available where an outcome type would be
unreasonable.

## Where this applies

Every one of these exists or is planned in CloudPos:

| Operation | Record | Then fail with |
| --- | --- | --- |
| PIN verification | failed-attempt counter, lockout | `PIN_INVALID`, `PIN_LOCKED` |
| Refresh rotation | family revocation | `REFRESH_TOKEN_REUSED` |
| Idempotency key | stored key and fingerprint | `IDEMPOTENCY_KEY_CONFLICT` |
| Payment attempt | attempt row, provider reference | provider failure code |
| Authorization check | audit entry | `FORBIDDEN` |
| Offline replay | negative stock adjustment, alert | warning in the per-order result |

## Consequences

- Service methods on these paths return outcome types rather than raw domain objects.
  This is more verbose and it is the point: the signature makes the failure path visible.
- Callers must handle every enum value. Java's exhaustive `switch` enforces it.
- Each such path needs a test that asserts **the record survived the failure**, not only
  that the failure occurred. `failedAttemptsPersistAcrossCalls` and
  `reusingAConsumedRefreshTokenRevokesTheWholeFamily` are the templates.
- Code review on any new `@Transactional` method asks one question: does this method
  write something and then throw? If yes, it is wrong.
