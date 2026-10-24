# Architecture decision records

One file per decision: context, decision, consequences. A decision changes only through a new or amended ADR.

| ADR | Title | Status |
|---|---|---|
| [001](ADR-001-synchronized-display.md) | Synchronized display of the analysed frame and its result | Accepted |
| [002](ADR-002-single-inference-thread.md) | One inference at a time on a dedicated thread, latest frame wins | Accepted |
| [003](ADR-003-signed-models-outside-apk.md) | Models outside the APK, signed, swapped only when idle | Accepted |
| [004](ADR-004-typed-telemetry.md) | Telemetry as a typed allowlist, aggregated in windows | Accepted |
| [005](ADR-005-no-nnapi.md) | No NNAPI: LiteRT GPU delegate with CPU fallback | Accepted |
| [006](ADR-006-module-boundaries.md) | Module boundaries enforced by Gradle and Konsist | Accepted |
| [007](ADR-007-test-frameworks.md) | Test frameworks | Accepted |
| [008](ADR-008-static-analysis.md) | Static analysis | Accepted |
| [009](ADR-009-tracer-port.md) | Tracer port without per-frame allocation | Accepted |
| [010](ADR-010-frame-buffer-ownership.md) | Frame buffer ownership | Accepted |
| [011](ADR-011-clock-and-latency.md) | Clock base and end-to-end latency | Accepted |
| [012](ADR-012-per-frame-allocation.md) | Scope of "no per-frame allocation" | Accepted |
| [013](ADR-013-inference-rate-and-degradation.md) | Inference rate and degradation | Accepted |
| [014](ADR-014-feature-first-modules.md) | Feature-first modules, one module per layer | Accepted |
| [015](ADR-015-first-real-loop.md) | First real loop (M2) choices | Accepted |
| [016](ADR-016-uvc-capture.md) | UVC capture (M3) choices | Accepted |
| [017](ADR-017-system-adapters.md) | System adapters (M4): thermal headroom and memory pressure | Accepted |

Amended in M1: ADR-003 (model and audit ports, swap lock), ADR-004 (telemetry string fields, events, windows).
Amended in M7: ADR-014 (capture-file, a debug/benchmark FrameSource), ADR-009 (RENDER section owner), ADR-005 (debug backend override).

## Template

```markdown
# ADR-NNN: Title

- Status: Proposed | Accepted | Superseded by ADR-XXX
- Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
