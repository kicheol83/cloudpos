# ADR 0002: Java 21 and Spring Boot with virtual threads

**Status:** Accepted
**Date:** 2026-09-05

## Context

The engineer's existing depth is in NestJS/TypeScript, with three completed Node
projects. The target market is Korean backend roles, where job descriptions are
dominated by Java and Spring, secondarily Kotlin. A fourth Node project adds little
new signal.

Separately, the workload is database-bound: at peak the system issues roughly 4,000
stock-movement writes per second through JDBC. Thread-per-request with blocking JDBC is
the limiting factor, not the HTTP layer.

## Decision

Java 21 with Spring Boot 3.5, Spring MVC on virtual threads
(`spring.threads.virtual.enabled=true`). Gradle with the Kotlin DSL, multi-module.

Kotlin is deferred. It offers no additional hiring signal over Java in this market and
would add language learning on top of framework learning.

WebFlux is used only in `realtime-gateway`, where the workload is connection-bound
rather than CPU- or JDBC-bound.

## Consequences

- Slower initial velocity than continuing in NestJS; the timeline absorbs this.
- Blocking JDBC remains the correctness-simple choice; virtual threads remove the
  thread-pool ceiling without a reactive rewrite.
- Pinning risk: any `synchronized` block around blocking I/O pins a carrier thread.
  `ReentrantLock` is used instead, and pinning is monitored with JFR in load tests.

## Alternatives considered

| Option | Rejected because |
| --- | --- |
| NestJS | Three existing Node projects; marginal signal. |
| Kotlin + Spring | Learning two things at once; no hiring advantage over Java here. |
| Spring WebFlux everywhere | Reactive does not remove a JDBC bottleneck; it removes debuggability. |
