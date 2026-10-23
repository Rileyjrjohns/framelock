# ADR-011: Clock base and end-to-end latency

- Status: Accepted
- Date: 2026-10-23

## Context

End-to-end latency is render time minus capture time, on the same clock (`elapsedRealtimeNanos`). Choreographer frame
times use `System.nanoTime`, CameraX timestamps depend on the sensor's timestamp source, and UVC timestamps use yet
another base. Nothing reported renders.

## Decision

- **Capture time**: the source timestamp when it is on the realtime base, otherwise the arrival time read from the
  `Clock` port. `StreamSpec.timestampSource` (`REALTIME` or `ARRIVAL`) records which one.
- **Render time**: `Clock.nowNanos()` read in the overlay draw pass and reported through the use case
  `ReportFrameRendered(frameId, sessionId, renderNanos)`.
- **Inference duration**: `InferenceResult.inferenceNanos`, measured by the engine adapter around the runtime call only.
- Delays (throttle, reconnection backoff) use coroutine `delay`; timestamps use the `Clock` port. In tests both run on
  the same virtual time.

## Consequences

The reported end-to-end latency excludes compositor and display latency; the README states it.
