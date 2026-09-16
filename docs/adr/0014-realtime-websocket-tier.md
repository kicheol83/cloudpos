# ADR 0014: Separately scaled WebSocket tier

**Status:** Accepted
**Date:** 2026-09-05

## Context

At 10,000 stores with four terminals each, roughly 40,000 persistent connections are
expected. Connection count scales independently of request rate, so hosting sockets in
the request/response services would force them to scale on the wrong signal.

Kitchen status, table state and low-stock alerts must reach terminals within seconds.

## Decision

- A dedicated `realtime-gateway` service, WebSocket with STOMP, built on WebFlux — the
  one place where a reactive stack matches the workload.
- The gateway is **stateless**. Subscription routing goes through Redis pub/sub, so any
  pod can serve any store; sticky sessions are not required.
- Roughly 10,000 connections per pod, 4–6 pods at target scale, autoscaled on
  **connection count**, not CPU.
- Delivery is **at-least-once**, deduplicated client-side by event ID.
- **The socket is an optimisation, never the source of truth.** On reconnect, terminals
  fetch full state over HTTP and reconcile. A terminal that has missed events is correct
  after reconnect without any replay protocol.

## Consequences

- Two transports for the same data (HTTP snapshot, WebSocket delta) must stay
  semantically identical, which is contract-tested.
- Redis becomes a availability dependency for realtime; its failure degrades to polling,
  which is an accepted degraded mode.
- Load testing must cover connection churn, not only message throughput.
