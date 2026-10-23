# ADR-010: Frame buffer ownership

- Status: Accepted
- Date: 2026-10-23

## Context

ADR-001 draws the analysed frame, so its buffer cannot be released right after `infer`. `StateFlow` conflates values a
slow collector never sees, so a buffer handed only through the state could leak. Frames emitted by a source and never
collected must be released too.

## Decision

- **Source → pipeline.** A `FrameSource` emits on a channel whose `onUndeliveredElement` closes the frame's buffer. The
  pipeline collects `events` before calling `open()`.
- **Inside the pipeline.** Every frame the pipeline receives is owned by it. It closes the buffer of every frame it
  drops (late, stale session, conflated, paused, after stop), at the moment it drops it.
- **Pipeline → display.** After a successful inference the frame moves to a `DisplaySlot`. Publishing the next
  annotated frame, or clearing the screen on disconnect or stop, closes the previous one under the slot's lock. The
  renderer reads pixels only inside `DisplaySlot.read { }`, under the same lock, and copies them into its own
  double-buffered bitmap. A buffer is therefore never closed while being read, and never read after being closed.
- Pools must hold at least three buffers: one being analysed, one displayed, one being filled.

## Consequences

`PipelineUiState` carries the ids and the result for display decisions; pixels are only reachable through the slot.
The slot and the state are updated one after the other, so the renderer draws the overlay only when the frame read in
the slot has the (session, id) of `PipelineUiState.annotated`. A new `Connected` clears the slot even without a
`Disconnected` before it. The pipeline waits for the slot lock to show a frame: the renderer copies pixels, nothing
else, while holding it.
