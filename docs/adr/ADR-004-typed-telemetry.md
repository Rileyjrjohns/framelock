# ADR-004: Telemetry as a typed allowlist, aggregated in windows

- Status: Accepted
- Date: 2026-10-22

## Context

Telemetry must never carry images or free text. Per-frame events would also cost battery and bandwidth.

## Decision

- Only `TelemetryEvent` subtypes are recorded, with primitive or enum fields. `ScreenId` and `ElementId` are enums,
  so tracking a new element is a reviewed code change.
- `TelemetryAllowlist` rejects anything else; a Konsist rule checks the event types' fields.
- `MetricsAggregator` aggregates pipeline metrics over 10 s windows. No per-frame events.
- Transport: capped Room table (oldest dropped first), periodic WorkManager batch upload with a network constraint,
  gzipped JSON. `LoggingUploader` by default.

## Consequences

### Amendment (2026-10-23, M1)

- The telemetry event for device information is `DeviceSnapshotRecorded`, distinct from the domain type
  `DeviceSnapshot`. Its `model` and `osVersion` are the only free-form strings allowed, bounded to
  `[A-Za-z0-9._ -]{1,64}`. `ModelVersion` uses `[A-Za-z0-9._-]{1,64}`. Causes and reasons are enums.
- `StartPipeline` records `DeviceSnapshotRecorded` once per start; the app records `AppSession` at launch (M2).
- `PipelineWindow`: a window with no frame is not emitted; percentiles use the nearest-rank method; latency fields are
  null when the window has no sample.
- The debug HUD reads a separate `LiveMetrics` state updated once per second; it is never sent as telemetry.
