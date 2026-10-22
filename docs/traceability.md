# Traceability

Each requirement of [architecture.md](architecture.md) maps to the tests that cover it. Status: **planned**
(milestone given), **covered** (tests exist and pass).

## Architecture and build (M0)

| ID | Requirement | Tests | Status |
|---|---|---|---|
| ARCH-1 | Core is pure Kotlin: no `android.*`, no DI annotations, only kotlinx-coroutines-core | `LayeringRulesTest`: core does not import android…; core uses no DI annotations; core main imports only…; `ModuleBoundariesPluginTest`: core module with an extra external library fails; … runtime only external library fails; compileOnly DI annotation in core fails | covered |
| ARCH-2 | Adapters depend on `:core:domain` only, never on each other; only `:app` sees every module | `ModuleBoundariesPluginTest`: adapter depending on another adapter fails; adapter depending on core application fails; core domain depending on any project fails; core application depending on an adapter fails; depending on app fails; main configuration depending on core testing fails; test configuration may depend on core testing; module without a role fails; valid dependency graph passes. `LayeringRulesTest`: domain does not import application; adapters do not import another adapter's package; adapters do not import core application | covered |
| ARCH-3 | Everything external behind a port; ports implemented only in adapters | `LayeringRulesTest`: ports are implemented only in adapters, core testing or test sources (no port yet, rule active from M1) | covered (vacuous until M1) |
| ARCH-4 | Manual DI, no Hilt or Koin | `LayeringRulesTest`: app uses no Hilt or Koin | covered |
| ARCH-5 | Architecture rules never pass on an empty scope | `LayeringRulesTest`: every module scope is non-empty | covered |
| PIPE-1a | No `limitedParallelism` in the pipeline (ADR-002) | `LayeringRulesTest`: pipeline does not call limitedParallelism | covered |
| CI-1 | Build, unit tests, ktlint, detekt, Konsist on every push | `.github/workflows/ci.yml` | covered |

## Core (M1)

| ID | Requirement | Status |
|---|---|---|
| DOM-1 | `FrameId` monotonic within a session | planned M1 |
| DOM-2 | `SessionId` incremented on every (re)connection | planned M1 |
| DOM-3 | Value object invariants (rectangles, scores, manifest, frame) | planned M1 |
| DOM-4 | `ImageBuffer.close()` returns the buffer to its pool | planned M1 |
| POL-1 | `ResultDisplayPolicy` | planned M1 |
| POL-2 | `InferenceRatePolicy` | planned M1 |
| POL-3 | `ReconnectPolicy` (backoff, cap, jitter) | planned M1 |
| POL-4 | `ModelSwapPolicy` | planned M1 |
| POL-5 | `TelemetryAllowlist` | planned M1 |
| PIPE-1 … PIPE-7 | Pipeline rules 1–7 (core part; rule 7 for the inference section only) | planned M1 |
| DISP-1 | A result is shown only with its own frame (ADR-001) | planned M1 |
| MOD-1 … MOD-4 | Swap only when idle; load before switching; rollback; telemetry and audit | planned M1 |
| TEL-1 … TEL-5 | Typed events; enum ids; 10 s windows; e2e on one clock; `PipelineWindow` fields | planned M1 |

## Later milestones

| ID | Requirement | Status |
|---|---|---|
| LRT-1 | LiteRT golden test on a fixed image, with tolerance (instrumented) | planned M2 |
| UI-T | `Modifier.track(ElementId)` routes clicks to `TrackUiInteraction` | planned M2 |
| PIPE-7b | Trace sections for capture, preprocess, postprocess, render | planned M2/M3 |
| UI-1 | "No signal" banner shown and overlay hidden on disconnect | planned M3 |
| MS-1 … MS-3 | Bad signature rejected; atomic activation; rollback on disk | planned M5 |
| TELA-1, TELA-2 | Telemetry buffer cap; batching | planned M6 |
| PIPE-6b, PERF-1 | No per-frame allocation (measured); performance results | planned M7 |
