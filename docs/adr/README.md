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

Planned (M1): Tracer port without per-frame allocation, frame buffer ownership for display, clock base and
end-to-end latency, scope of "no per-frame allocation", inference rate and degradation table, model and audit
ports, telemetry string fields. Planned (M3): UVC library and its USB permission handling.

## Template

```markdown
# ADR-NNN: Title

- Status: Proposed | Accepted | Superseded by ADR-XXX
- Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
