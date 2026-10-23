# ADR-012: Scope of "no per-frame allocation"

- Status: Accepted
- Date: 2026-10-23

## Context

Taken literally, the rule cannot hold: `infer()` returns a new `InferenceResult`, a `SourceEvent` is created per frame,
and a `StateFlow` needs a new immutable value per emission.

## Decision

"No per-frame allocation in steady state" applies to pixel buffers, tensors, bitmaps and large arrays: these come from
pools and are reused. Small objects (events, results, state values) are allowed; their cost is measured in M7 and
pooled only if a trace shows it matters. Metrics use preallocated arrays.

## Consequences

Steady-state allocation is checked in M7 with the memory profiler, not by a JVM unit test.
