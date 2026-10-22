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

The few bounded string fields (device model, OS version) are specified in M1 (amendment to this ADR).
