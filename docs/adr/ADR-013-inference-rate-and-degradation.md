# ADR-013: Inference rate and degradation

- Status: Accepted
- Date: 2026-10-23

## Context

`InferenceRatePolicy` maps `(ThermalLevel, MemoryPressure)` to a target rate or a pause, but no values were given.

## Decision

| Thermal | Rate | | Memory | Rate |
|---|---|---|---|---|
| NONE, LIGHT | unlimited | | NORMAL | unlimited |
| MODERATE | 15 fps | | MODERATE | 15 fps |
| SEVERE | 10 fps | | CRITICAL | pause |
| CRITICAL | 5 fps | | | |
| EMERGENCY, SHUTDOWN | pause | | | |

- The decision is the most restrictive of the two, and names its cause: `THERMAL`, `MEMORY` or `THERMAL_AND_MEMORY`
  when both impose the same limit.
- "Unlimited" means one inference at a time, as fast as the engine goes (ADR-002). A capped rate spaces inference
  starts by at least `1 / fps`.
- States: `Running(rate)` when nothing limits; `Degraded(cause, rate)` when a cap applies; `Paused(cause)` on pause.
  An engine failure on one frame gives `Degraded(ENGINE_ERROR, rate)` until the next successful inference.
- `droppedFrames` in telemetry counts every frame the pipeline closed without analysing it; the HUD shows the split by
  `DropReason` (conflated, late, stale session, paused, stopped).

## Consequences

The values are a starting point, tuned in M4/M7 with measurements.
