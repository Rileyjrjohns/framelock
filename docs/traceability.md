# Traceability

Each requirement of [architecture.md](architecture.md) maps to the tests that cover it. Status: **planned**
(milestone given), **covered** (tests exist and pass).

## Architecture and build (M0)

Gradle tests are in `build-logic/boundaries` (`ModuleBoundariesPluginTest`), Konsist tests in `architecture-tests`
(`LayeringRulesTest` on the repository, `RuleFixturesTest` on fixtures that break each rule).

| ID | Requirement | Tests | Status |
|---|---|---|---|
| ARCH-1 | Core is pure Kotlin: no `android.*`, no DI, only kotlinx-coroutines-core | Konsist: core does not reference android, androidx or com google android; core production imports only kotlin, kotlinx coroutines and the per-module allowlist; fixtures: android referenced by its qualified name in core is caught, not when in a comment; allowlisted class name does not allow classes sharing its prefix. Gradle: core module with an extra external library fails; core module with a runtime only external library fails; compileOnly DI annotation in core fails; files dependency in core fails | covered |
| ARCH-2 | Adapters depend on `:core:domain` only, never on each other; only `:app` sees every module | Gradle: adapter depending on another adapter fails; adapter depending on core application fails; core domain depending on any project fails; core application depending on an adapter fails; depending on app fails; main configuration depending on core testing fails; test configuration may depend on core testing; dependency substitution to another adapter fails; custom configuration extended by main cannot carry core testing; production classpath whose name contains Test cannot carry core testing; test fixtures of core cannot carry core testing to an adapter; transitive edge is checked; module without a role fails; valid dependency graph passes. Konsist: domain does not reference application; adapters do not reference another adapter's package; adapters do not reference core application | covered |
| ARCH-3 | Ports live in `core.domain.port` and are implemented only in adapters | Konsist: ports are implemented only in adapters, core testing or test sources; fixture: port implementations outside adapters are caught however they are written | covered (rule proven on fixtures; applies to real ports from M1) |
| ARCH-4 | Manual DI: no Hilt, no Koin, no DI annotations anywhere | Konsist: no module references a DI framework or DI annotations; fixture: DI imports and qualified DI annotations are caught in an adapter. Gradle: DI library in an adapter fails | covered |
| ARCH-5 | Architecture rules never pass on an empty scope | Konsist: every module scope is non-empty; each fixture test | covered |
| PIPE-1a | No `limitedParallelism` in the core (ADR-002) | Konsist: core does not call limitedParallelism; fixture: limitedParallelism is caught in any core module, not in comments or strings | covered |
| CI-1 | Build, unit tests, ktlint, detekt, Konsist on push to `main` and on pull requests | `.github/workflows/ci.yml` | planned (first run on first push) |

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
