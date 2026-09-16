# ADR 0021: Device credential plus staff PIN is the authentication unit

**Status:** Accepted
**Date:** 2026-09-17

## Context

POS staff authenticate dozens of times a shift, on a shared tablet, often with wet or
gloved hands. A password is not workable. The UI kit shows the real-world pattern: pick
your face from a grid, type four digits.

A four-digit PIN has about 13 bits of entropy. On its own it is not a credential.

## Decision

**The credential is the device plus the PIN, never the PIN alone.**

- A device is registered by a manager and paired once. Pairing issues a 256-bit secret,
  returned exactly once and stored only as a SHA-256 hash. The device holds it.
- Staff login requires `device_id` + `device_secret` + `staff_id` + `pin`.
- A PIN is only meaningful on a device registered to the staff member's own store. A
  stolen PIN is useless without a paired device, and a stolen device is useless without
  a PIN.
- Revoking a device invalidates every session on it immediately.

**Hashing is chosen per credential type.** The device secret is high-entropy random, so
SHA-256 is sufficient; a slow hash would add latency without adding security. The PIN is
low-entropy, so it uses Argon2id. Using one algorithm for both would be wrong in one
direction or the other.

**Lockout is on the staff member, not the device.** Five consecutive failures lock the
PIN for fifteen minutes. On lockout the counter resets, so an expiring lock grants a
fresh five attempts rather than re-locking on the next mistake.

**Tenant resolution at login uses a directory table, not the request body.** Services
never trust a tenant identifier from a client. `device_directory` maps `device_id` to
tenant and store and carries no RLS, so an unauthenticated login can resolve its tenant
before any tenant-scoped query runs. The same pattern serves device pairing
(`device_pairing`) and refresh (`<tenant>.<random>` token prefix).

**Tokens.** Access tokens are RS256 JWTs, 15 minutes, carrying `tid`, `sid`, `did`,
`role` and `shift`. The identity service holds the private key and publishes JWKS; the
gateway only verifies. A shared HMAC secret was rejected: anything able to verify would
also be able to mint.

**Refresh tokens rotate, and reuse is treated as theft.** Each refresh consumes the
presented token and issues a successor in the same family. Presenting a consumed token
revokes the entire family.

## Consequences

- Manager approval for privileged actions reuses the PIN path and issues a separate
  two-minute approval token scoped to one action, rather than elevating the session.
- A lost device is an operational event: revoke it, and every session dies.
- Signing keys are generated at startup in development, so restarts invalidate access
  tokens. Production requires externally managed keys and an overlap window during
  rotation. Deferred to phase 9 and tracked here.
- Login opens or resumes a shift, so the token always carries a `shift` claim for staff
  who are on duty. Sales endpoints can require it.

## Alternatives considered

| Option | Rejected because |
| --- | --- |
| PIN only | 13 bits of entropy is not a credential, and any tablet could impersonate any store. |
| Username and password | Unusable at POS speed; staff would share and write them down. |
| Long-lived device token with no staff identity | Every action would be attributed to the terminal, destroying the audit trail that discount and void tracking depends on. |
| Shared HMAC signing secret | Any service able to verify could also mint tokens for any role. |
