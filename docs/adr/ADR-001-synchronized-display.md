# ADR-001: Synchronized display of the analysed frame and its result

- Status: Accepted
- Date: 2026-10-22

## Context

Results arrive after the frame they were computed from. If the UI shows the live preview and overlays the latest
result on it, the overlay describes an older image: with fast camera motion it drifts from the object it describes.

## Decision

The UI draws the analysed frame and its overlay together: what you see is what was analysed.
`ResultDisplayPolicy` allows a result only with the frame it was computed from (same `sessionId` and `frameId`).

Rejected: live preview plus an overlay hidden when too old. It still shows wrong boxes during motion, and the
"too old" threshold has no correct value.

## Consequences

- Display latency equals inference latency, so keeping inference fast matters (see `docs/perf/`).
- The analysed frame's buffer must live until it is drawn; ownership is specified in M1.
- On disconnection, the result is cleared in the same state emission as `NoSignal`.
