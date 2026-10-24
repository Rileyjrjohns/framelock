# ADR-009: Tracer port without per-frame allocation

- Status: Accepted
- Date: 2026-10-23

## Context

The original port `Tracer.section(name: String, block: () -> T)` allocates a lambda per call, and per frame on the hot
path. `androidx.tracing` sections must also end on the thread that began them, and free-text section names invite
typos and unbounded cardinality.

## Decision

- Port: `Tracer.begin(section: TraceSection)` / `Tracer.end()`. `TraceSection` is an enum: `CAPTURE`, `PREPROCESS`,
  `INFERENCE`, `POSTPROCESS`, `RENDER`.
- The domain provides `inline fun <T> Tracer.trace(section, block)` with `begin` before and `end` in `finally`, so
  callers never unbalance a section and no lambda is allocated.
- Ownership: the pipeline traces `INFERENCE`; the source adapters trace `CAPTURE`; the LiteRT adapter traces
  `PREPROCESS` and `POSTPROCESS`; the app traces `RENDER`. Each receives the `Tracer` port.

## Consequences

The androidx.tracing implementation lives in `:features:live:system` (M4). Sections that cross threads (render) use async
sections there, behind the same port.

### Amendment (2026-10-24, M7)

`RENDER` is traced by `FrameRenderer` in `:features:live:ui`, which receives the `Tracer` port from `:app` (the UI module
may reach the kernel). It wraps the I420 to ARGB conversion and the bitmap upload; both run on the main thread, so a
plain section is enough and no async section was needed.
