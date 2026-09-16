# ADR 0001: Record architecture decisions

**Status:** Accepted
**Date:** 2026-09-05

## Context

CloudPos is a multi-tenant restaurant POS SaaS targeting 10,000 stores, built by a
single engineer over roughly a year. Decisions made in month one will be questioned in
month ten, and in interviews. Without a record, the reasoning is lost and the answer
degrades to "that is how I did it".

## Decision

Every significant architectural decision is recorded as a numbered ADR in `docs/adr/`,
using the format: Context, Decision, Consequences, Alternatives considered.

An ADR is required when a decision is expensive to reverse, constrains other components,
or rejects a plausible alternative. ADRs are immutable once accepted; a changed decision
becomes a new ADR that supersedes the old one.

## Consequences

- Each rejected alternative carries the metric that would reverse the decision.
- The ADR set is the primary artefact reviewers read before the code.
- Writing the ADR before implementing is the norm, not documentation after the fact.
