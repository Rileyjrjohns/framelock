# Traceability

Each requirement of [architecture.md](architecture.md) maps to the tests that cover it. Status: **planned**
(milestone given), **covered** (tests exist and pass).

## Architecture and build (M0)

Gradle tests are in `build-logic/boundaries` (`ModuleBoundariesPluginTest`), Konsist tests in `architecture-tests`
(`LayeringRulesTest` on the repository, `RuleFixturesTest` on fixtures that break each rule).

| ID | Requirement | Tests | Status |
|---|---|---|---|
| ARCH-1 | Pure modules (`shared:*`, `domain`, `application`): no `android.*`, no DI, only kotlinx-coroutines-core | Konsist: pure modules do not reference android, androidx or com google android; pure modules import only kotlin, kotlinx coroutines, the project and their JDK allowlist; fixtures: android referenced by its qualified name in a pure module is caught, not when in a comment; allowlisted class name does not allow classes sharing its prefix. Gradle: pure module with an extra external library fails; pure module with a runtime only external library fails; compileOnly DI annotation in a pure module fails; files dependency in a pure module fails; DI library brought transitively is refused in a pure module | covered |
| ARCH-2 | Layers depend inward within a feature; adapters never depend on each other; only `:app` sees every module (ADR-014) | Gradle: adapter depending on another adapter fails; adapter depending on an application fails; domain depending on its application fails; application depending on an adapter fails; kernel depending on any project fails; depending on app fails; main configuration depending on testing fails; test configuration may depend on testing; dependency substitution to another adapter fails; custom configuration extended by main cannot carry testing; production classpath whose name contains Test cannot carry testing; test fixtures of a domain cannot carry testing to an adapter; transitive edge is checked; module without a role fails; valid dependency graph passes | covered |
| ARCH-2b | Features depend on each other in one direction (`models → live`, `telemetry → live, models`) | Gradle: upstream feature depending on a downstream feature fails; downstream feature may depend on an upstream feature; module of an unknown feature fails | covered |
| ARCH-3 | Ports live in a port package and are implemented only in adapters | Konsist: ports are implemented only in adapters, shared testing or test sources; fixture: port implementations outside adapters are caught however they are written | covered |
| ARCH-4 | Manual DI: no Hilt, no Koin, no DI annotations anywhere | Konsist: no module references a DI framework or DI annotations; fixture: DI imports and qualified DI annotations are caught in an adapter. Gradle: DI library in an adapter fails; DI library brought transitively by a third-party library is allowed outside pure modules | covered |
| ARCH-5 | Architecture rules never pass on an empty scope | Konsist: every module scope is non-empty; each fixture test | covered |
| PIPE-1a | No `limitedParallelism` in pure modules (ADR-002) | Konsist: pure modules do not use limitedParallelism; fixture: limitedParallelism is caught in any pure module, not in comments or strings | covered |
| CI-1 | Build, unit tests, ktlint, detekt, Konsist on push to `main` and on pull requests | `.github/workflows/ci.yml` (first green run: GitHub Actions run 39921498870) | covered |

## Core (M1)

Tests are named by class; each class lists, in its test names, the cases it covers. Pipeline tests run on virtual
time with the fakes of `:shared:testing`, except `InferencePipelineThreadingTest`, which uses a real thread.

| ID | Requirement | Tests (module → class) | Status |
|---|---|---|---|
| DOM-1 | `FrameId` monotonic within a session | live:domain → `FrameIdTest` | covered |
| DOM-2 | `SessionId` incremented on every (re)connection; a non-increasing one is refused | live:domain → `SessionIdTest`; live:application → `InferencePipelineConnectionTest` (new session adopts an incremented SessionId; Connected with a non-increasing SessionId is rejected and its frames dropped) | covered |
| DOM-3 | Value object invariants (rectangles, scores, results, frames, manifests) | live:domain → `NormalizedRectTest`, `DetectionTest`, `FrameTest`; kernel → `ModelManifestTest` | covered |
| DOM-4 | `ImageBuffer.close()` returns the buffer to its pool, exactly once | testing → `FakeImageBufferPoolTest`; live:application → `InferencePipelineBufferLifecycleTest` | covered |
| POL-1 | `ResultDisplayPolicy` | live:domain → `ResultDisplayPolicyTest` | covered |
| POL-2 | `InferenceRatePolicy` (ADR-013 table) | live:domain → `InferenceRatePolicyTest` | covered |
| POL-3 | `ReconnectPolicy` (backoff, cap, jitter) | live:domain → `ReconnectPolicyTest` | covered |
| POL-4 | `ModelSwapPolicy` | models:domain → `ModelSwapPolicyTest` | covered |
| POL-5 | `TelemetryAllowlist` (known families only) | telemetry:domain → `TelemetryAllowlistTest`; telemetry:application → `TelemetryRecorderTest` | covered |
| PIPE-1 | One inference at a time on the engine's own thread (ADR-002) | live:application → `InferencePipelineThreadingTest` | covered |
| PIPE-2 | Latest frame wins; drops counted and released at once | live:application → `InferencePipelineConflationTest` | covered |
| PIPE-3 | Late frames and frames of a previous session dropped | live:application → `InferencePipelineOrderingTest` | covered |
| PIPE-4 | Disconnection: `NoSignal`, result cleared in the same emission, in-flight result discarded, reconnection by policy | live:application → `InferencePipelineConnectionTest` | covered |
| PIPE-5 | Rate lowered or paused under pressure, the state says why; never crashes | live:application → `InferencePipelineDegradationTest` | covered |
| PIPE-6a | Every buffer released in all paths (ADR-010) | live:application → `InferencePipelineBufferLifecycleTest` | covered |
| PIPE-7 | `INFERENCE` trace section around each inference (ADR-009) | live:application → `InferencePipelineTracingTest` | covered |
| DISP-1 | A result is shown only with its own frame (ADR-001) | live:application → `InferencePipelineDisplayInvariantTest`, `InferencePipelineConnectionTest` | covered |
| MOD-1 | Swap only when idle or without signal | models:domain → `ModelSwapPolicyTest`; models:application → `ActivateModelTest`, `RollbackModelTest`, `ImportModelTest`; live:application → `InferencePipelineModelLoadingTest` | covered |
| MOD-2 | Load and warm up before the pointer switches; previous model kept on failure | models:application → `ActivateModelTest`, `RollbackModelTest`; live:application → `InferencePipelineModelLoadingTest` | covered |
| MOD-3 | Rollback to the previous version | models:application → `RollbackModelTest` | covered |
| MOD-4 | Telemetry and audit on every import, activation, rejection and rollback | models:application → `ActivateModelTest`, `RollbackModelTest`, `ImportModelTest` | covered |
| TEL-1 | Typed events only; unknown families rejected | telemetry:domain → `TelemetryAllowlistTest` | covered |
| TEL-2 | `ScreenId` and `ElementId` are enums; tracking goes through the allowlist | telemetry:application → `TelemetryRecorderTest` | covered |
| TEL-3 | 10 s windows, no per-frame event | live:application → `MetricsAggregatorTest`, `InferencePipelineMetricsTest` | covered |
| TEL-4 | e2e latency = render − capture on the same clock (ADR-011) | live:application → `MetricsAggregatorTest`, `InferencePipelineMetricsTest`, `PipelineUseCasesTest` | covered |
| TEL-5 | `PipelineWindow` fields (nearest-rank p50/p95, fps, drops) | live:application → `PercentilesTest`, `MetricsAggregatorTest` | covered |
| UC-1 | Start/Stop/Observe use cases; device snapshot once per start | live:application → `PipelineUseCasesTest` | covered |

## Later milestones

| ID | Requirement | Status |
|---|---|---|
| LRT-1 | LiteRT golden test on a fixed image, with tolerance (instrumented) | planned M2 |
| UI-T | `Modifier.track(ElementId)` routes clicks to `TrackUiInteraction` | planned M2 |
| PIPE-7b | Trace sections for capture, preprocess, postprocess, render (ADR-009 ownership) | planned M2/M3 |
| UI-1 | "No signal" banner shown and overlay hidden on disconnect | planned M3 |
| MS-1 … MS-3 | Bad signature rejected; atomic activation; rollback on disk | planned M5 |
| TELA-1, TELA-2 | Telemetry buffer cap; batching | planned M6 |
| PIPE-6b, PERF-1 | No per-frame allocation (measured); performance results | planned M7 |
