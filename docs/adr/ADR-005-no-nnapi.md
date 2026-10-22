# ADR-005: No NNAPI: LiteRT GPU delegate with CPU fallback

- Status: Accepted
- Date: 2026-10-22

## Context

NNAPI is deprecated since Android 15. LiteRT ships two Android APIs: the `Interpreter` line (1.x) with
`GpuDelegate` and `CompatibilityList`, and the newer `CompiledModel` line (2.x), whose GPU artifact no longer contains
`GpuDelegate` and which pulls an alpha Play services SDK.

## Decision

- Use LiteRT **1.4.2** (`com.google.ai.edge.litert:litert` and `litert-gpu`), with the GPU delegate when
  `CompatibilityList` reports support, and the CPU (XNNPACK) path otherwise.
- No NNAPI delegate.
- Never mix 1.x and 2.x LiteRT artifacts.

## Consequences

- 1.4.2 is in maintenance mode. The 2.x `CompiledModel` API is a candidate for the M7 performance pass, measured
  against this baseline.
- GPU is not assumed faster: for small int8 models a multi-thread CPU run can win. The choice is measured on device.
