# Traceability

Each requirement of [architecture.md](architecture.md) maps to the tests that cover it. Status: **planned**
(milestone given), **covered** (tests exist and pass).

## Architecture and build (M0)

Gradle tests are in `build-logic/boundaries` (`ModuleBoundariesPluginTest`), Konsist tests in `architecture-tests`
(`LayeringRulesTest` on the repository, `RuleFixturesTest` on fixtures that break each rule).

| ID | Requirement | Tests | Status |
|---|---|---|---|
| ARCH-1 | Pure modules (`shared:kernel`, `shared:testing`, `domain`, `application`): no `android.*`, no DI, only kotlinx-coroutines-core | Konsist: pure modules do not reference android, androidx or com google android; pure modules import only kotlin, kotlinx coroutines, the project and their JDK allowlist; fixtures: android referenced by its qualified name in a pure module is caught, not when in a comment; allowlisted class name does not allow classes sharing its prefix. Gradle: pure module with an extra external library fails; pure module with a runtime only external library fails; compileOnly DI annotation in a pure module fails; files dependency in a pure module fails; DI library brought transitively is refused in a pure module | covered |
| ARCH-2 | Layers depend inward within a feature; adapters never depend on each other; only `:app` sees every module (ADR-014) | Gradle: adapter depending on another adapter fails; adapter depending on an application fails; domain depending on its application fails; application depending on an adapter fails; kernel depending on any project fails; depending on app fails; main configuration depending on testing fails; test configuration may depend on testing; dependency substitution to another adapter fails; custom configuration extended by main cannot carry testing; production classpath whose name contains Test cannot carry testing; test fixtures of a domain cannot carry testing to an adapter; transitive edge is checked; module without a role fails; valid dependency graph passes. Unit: `ModuleRoleTest` pins the whole ADR-014 reach table, for production, test and Android variant classpaths | covered |
| ARCH-2b | Features depend on each other in one direction (`models → live`, `telemetry → live, models`) | Gradle: upstream feature depending on a downstream feature fails; downstream feature may depend on an upstream feature; module of an unknown feature fails | covered |
| ARCH-3 | Ports live in a port package and are implemented only in adapters | Konsist: ports are implemented only in adapters, shared testing or test sources; fixture: port implementations outside adapters are caught however they are written | covered |
| ARCH-4 | Koin confined to app; all other DI frameworks and annotations forbidden | Konsist `referencesToDependencyInjection`; pure/adapter/UI Koin fixtures and app non-Koin fixture; Gradle boundary tests for app-only Koin | covered |
| ARCH-5 | Architecture rules never pass on an empty scope, and skip only root-level hidden directories | Konsist: every module scope is non-empty; each fixture test; fixture: hidden directories are skipped only at the repository root | covered |
| PIPE-1a | No `limitedParallelism` in pure modules (ADR-002) | Konsist: pure modules do not use limitedParallelism; fixture: limitedParallelism is caught in any pure module, not in comments or strings | covered |
| CI-1 | Build, unit tests, ktlint, detekt, Konsist on push to `main` and on pull requests | `.github/workflows/ci.yml` (green on M0: GitHub Actions run 39921498870) | covered for M0; M1 runs on its first push |
| CI-2 | Release APK as an artifact on tag `v*`, signed from repository secrets when present, never from the repository | `.github/workflows/release.yml`, `app/build.gradle.kts` (signing from environment); signed and unsigned `assembleRelease` checked locally, `apksigner verify` (2026-10-24); first tag `v0.1.0` built green on GitHub Actions (run 40112244704, artifact `framelock-v0.1.0`, unsigned: no secrets set) | covered |

## Composition, navigation and design system (ADR-018/019)

The historical milestone checks below predate this refactor. The API 36 follow-up is recorded in
[device validation, 2026-10-28](device-validation-2026-10-28.md); untested device cases remain explicit there.

| ID | Requirement | Automated verification | Device verification |
|---|---|---|---|
| DI-1 | Whole app graph has constructor dependencies; incomplete graph fails | app → `KoinGraphTest` | API 36 startup and worker factory checked, 2026-10-28; fresh bootstrap not rerun |
| NAV-1 | Pure redirects, ordered commands buffered without a collector, consumed commands never replay | app → `AppNavigatorTest`; saved back stack owned by the navigation host | API 36 toolbar/system back and locale recreation checked, 2026-10-28 |
| DS-1 | Shared UI reaches no feature and accepts only its external allowlist | `ModuleRoleTest`, `verifyModuleBoundaries` | not yet run |
| DS-2 | Glass, accent, status and overlay text maintain AA contrast | designsystem → `GlassPaletteTest`, `AppGlassContrastTest`, `ContrastTest`, `OverlayPaletteTest`; catalogue previews compile | API 36 glass and layout inspected, 2026-10-28; API 29–30 not yet run |
| UI-R1 | Feature UI cannot import Material button/card/menu/input/progress primitives | `UiRules.materialImports`, fixture `ui-material` | not yet run |
| UI-R2 | One public top-level UI declaration per matching filename | `UiRules.declarations`, fixture `ui-declarations` | not yet run |
| UI-R3 | Feature UI files have at most 400 lines | `UiRules.fileLength`, fixture `ui-length` (400 accepted, 401 rejected) | not yet run |
| UI-R4 | No literal Text or contentDescription in feature UI | `UiRules.literalText`, fixture `ui-text` | not yet run |
| UI-R5 | Dimensions and colours come from tokens, except 0.dp | `UiRules.literalTokens`, fixture `ui-tokens` | not yet run |
| UI-L1 | Resource keys name feature/screen/element; French keys and placeholders match English | `UiResourcesTest`; app → `SourceSelectionTest` locale relabeling preserves choice/device names | API 36 English/French screens, source labels and outcomes checked, 2026-10-28 |
| UI-S1 | State-driven screens and portrait/landscape/HUD/menu/Models states | Existing ViewModel tests; eight screen previews compile; rendering and layout tests retained | API 36 portrait/landscape/HUD/menu/Models checked, 2026-10-28; HudLayoutTest passed |

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
| PIPE-4 | Disconnection: `NoSignal`, result cleared in the same emission, in-flight result discarded, reconnection by policy (one loop, backoff reset only when frames flow, no retry on a refused permission, failing event flow = `STREAM_ERROR`) | live:application → `InferencePipelineConnectionTest`, `InferencePipelineRobustnessTest` | covered |
| PIPE-5 | Rate lowered or paused under pressure, the state says why; never crashes (failing monitors, sinks, event flows, native load errors) | live:application → `InferencePipelineDegradationTest`, `InferencePipelineRobustnessTest`, `InferencePipelineStressTest`; `PipelineUseCasesTest` | covered |
| PIPE-6a | Every buffer released in all paths (ADR-010) | live:application → `InferencePipelineBufferLifecycleTest`, `InferencePipelineLifecycleTest`, `InferencePipelineStressTest` | covered |
| PIPE-7 | `INFERENCE` trace section around each inference (ADR-009) | live:application → `InferencePipelineTracingTest` | covered |
| DISP-1 | A result is shown only with its own frame (ADR-001) | live:application → `InferencePipelineDisplayInvariantTest`, `InferencePipelineConnectionTest`, `InferencePipelineRobustnessTest` (a result computed for another frame is never published), `InferencePipelineStressTest` | covered |
| MOD-1 | Swap only when idle or without signal | models:domain → `ModelSwapPolicyTest`; models:application → `ActivateModelTest`, `RollbackModelTest`, `ImportModelTest`, `RealPipelineSwapTest` (on the real pipeline); live:application → `InferencePipelineModelLoadingTest` | covered |
| MOD-2 | Load before the pointer switches, atomically; previous model kept or loaded back on failure; no session starts in the middle | models:application → `ActivateModelTest`, `RollbackModelTest`, `RealPipelineSwapTest`; live:application → `InferencePipelineModelLoadingTest` | covered |
| MOD-3 | Rollback to the previous version | models:application → `RollbackModelTest` | covered |
| MOD-4 | Telemetry and audit on every import, activation, rejection and rollback | models:application → `ActivateModelTest`, `RollbackModelTest`, `ImportModelTest` | covered |
| TEL-1 | Typed events only; unknown families rejected; fields are primitives, enums, value classes or bounded strings | telemetry:domain → `TelemetryAllowlistTest`; Konsist: telemetry events carry only primitives, enums, value classes and bounded strings; fixture: telemetry fields that could carry an image or free text are caught | covered |
| TEL-2 | `ScreenId` and `ElementId` are enums; tracking goes through the allowlist | telemetry:application → `TelemetryRecorderTest` | covered |
| TEL-3 | 10 s windows, no per-frame event | live:application → `MetricsAggregatorTest`, `InferencePipelineMetricsTest` | covered |
| TEL-4 | e2e latency = render − capture on the same clock (ADR-011) | live:application → `MetricsAggregatorTest`, `InferencePipelineMetricsTest`, `PipelineUseCasesTest` | covered |
| TEL-5 | `PipelineWindow` fields (nearest-rank p50/p95, fps, drops) | live:application → `PercentilesTest`, `MetricsAggregatorTest` | covered |
| UC-1 | Start/Stop/Observe use cases; device snapshot once per start | live:application → `PipelineUseCasesTest` | covered |
| LIFE-1 | start, stop, close and swaps serialised; a stop is never lost and always completes; a closed pipeline refuses to start (ADR-003) | live:application → `InferencePipelineLifecycleTest` (real threads), `InferencePipelineRobustnessTest` | covered |

## Capture card (M3)

The UVC source itself needs a device and a capture card; its decisions are testable only where they are pure.

| ID | Requirement | Tests (module → class) | Status |
|---|---|---|---|
| UVC-1 | `UvcFrameSource`: first attached UVC device, USB permission, open, stream as `RGBA_8888` frames in a pool, teardown on every end, reconnection by `open()` | capture-uvc → `UvcFrameSource`; streamed from a USB webcam on device (2026-10-23, ADR-016 measurements); unplug/replug and an HDMI capture card not yet checked | covered (manual, partly) |
| UVC-2 | Capture mode: size closest to 1280x720, YUYV at 30 fps or more, else MJPEG at 30 fps or more, at the lowest such rate (highest rate below that) (ADR-016) | capture-uvc → `CaptureModeSelectorTest` | covered |
| UVC-3 | No-signal watchdog: `SIGNAL_LOST` after 2 s without a frame, `OPEN_FAILED` after 5 s without a first frame, one verdict per stream | capture-uvc → `FrameWatchdogTest` | covered |
| UVC-4 | RGBA pool: tight copy, skip when empty or of another size, release once, replaced on a size change | capture-uvc → `RgbaFramePoolTest` | covered |
| UVC-5 | A device is UVC when its class or one of its interfaces is video | capture-uvc → `UvcDeviceClassTest` | covered |
| UVC-6 | Source choice: external camera, then UVC device, then back camera; kept until `close()`; events merged | app → `SelectingFrameSourceTest` | covered |
| DOM-5 | Session ids from one thread-safe sequence shared by all sources | live:domain → `SessionIdTest` | covered |
| PIPE-4b | `SIGNAL_LOST` gives `NoSignal` and is retried like `DEVICE_DETACHED`; a `Disconnected` raised while a reconnection is opening is retried | live:application → `InferencePipelineConnectionTest` | covered |

## System adapters (M4)

Pure mappings are tested on the JVM (`ThermalHeadroomTest`, `MemoryPressureSignalsTest`, `SystemMappingTest` in
`:features:live:system`); the platform calls around them need a device. Thresholds and sources: ADR-017.

| ID | Requirement | Tests | Status |
|---|---|---|---|
| SYS-1 | Thermal level is the worse of the status listener and the forecast headroom (API 30+); API 29 uses the listener only | system → `ThermalHeadroomTest` (worse of two levels); `SystemMappingTest` (status mapping); the combination in `PowerManagerThermalMonitor` is not unit tested | covered (combination untested) |
| SYS-2 | Headroom to level: documented thresholds, never above SEVERE, release margin against flapping, NaN ignored | system → `ThermalHeadroomTest` | covered |
| SYS-3 | Headroom polled every 2 s only while collected; NaN or a failing call keeps the level | system → `ThermalHeadroomTest` (virtual time) | covered |
| SYS-4 | Trim levels to pressure (RUNNING_* only), hold and decay of a trim signal (30 s) | system → `MemoryPressureSignalsTest` | covered |
| SYS-5 | `MemoryInfo` to pressure, threshold factors and release margin | system → `MemoryPressureSignalsTest`; thresholds on a device: not yet run | covered (device not yet run) |
| SYS-6 | Callbacks registered while collected and unregistered on cancel; polling every 2 s (`AndroidMemoryPressureMonitor`) | none automated | implemented, untested |
| SYS-7 | Degradation wired end to end: `SystemModule` and `LiveModule` wire the real monitors; banner shows Degraded or Paused with the cause | live:application → `InferencePipelineDegradationTest` (ADR-013 table, causes, resume); checked on device (2026-10-23, Android 16): thermal override 3 → Degraded: thermal, 5 → Paused: thermal, reset → Running; `RUNNING_CRITICAL` → Paused: memory, back to Running after 30 s | covered |
| SYS-8 | Device snapshot once per start with the fields of architecture.md | live:application → `PipelineUseCasesTest`; system → `SystemMappingTest` (bounded tokens, battery) | covered |
| SYS-9 | Debug HUD shows thermal level and memory pressure | `Hud` thermal and memory rows fed by `LiveSignals`; no UI test; seen on device (2026-10-23) | implemented, checked manually |

## Model store (M5)

Logic is in plain classes tested on the JVM with temporary directories and keys generated by the tests
(`:features:models:store`); the Models screen and the platform wiring need a device. Format and rules: ADR-003,
amendment M5.

| ID | Requirement | Tests (module → class) | Status |
|---|---|---|---|
| MS-1 | Bad signature rejected (untrusted key, tampered manifest, missing or garbage `manifest.sig`); nothing installed, staging emptied; any trusted key accepted | store → `FileModelRepositoryTest` | covered |
| MS-2 | Bad hash rejected: model or labels differ from the signed hash; same version with other content refused; same package again is idempotent | store → `FileModelRepositoryTest` | covered |
| MS-3 | Malformed manifest rejected: missing or invalid field, version `..`, tensor spec, labels file without hash or the reverse | store → `ManifestFileTest`, `FileModelRepositoryTest` | covered |
| MS-4 | Zip rules: unknown names, path separators and parent references, directory entries, duplicates, size limits counted on bytes read, truncated or invalid zip | store → `PackageExtractorTest`, `FileModelRepositoryTest` | covered |
| MS-5 | Atomic activation: pointers replaced by rename, previous kept, survive a new repository, unknown version refused, a version named `active` does not collide | store → `FileModelRepositoryTest`; a crash between the two pointer writes is not simulated | covered (crash untested) |
| MS-6 | Rollback to the previous version on disk, repeatable, refused without one | store → `FileModelRepositoryTest`; swap under the pipeline lock: models:application → `ActivateModelTest`, `RollbackModelTest`, `RealPipelineSwapTest` | covered |
| MS-7 | Audit lines stamped with the wall clock at write time, enums and versions only, rotated at the cap, a failing write dropped | store → `FileAuditLogTest` | covered |
| MS-8 | `scripts/sign-model.sh` output (key pair, DER signature, manifest, zip) is accepted by the store; keys never overwritten | store → `SigningScriptTest` (needs bash, openssl, zip; skipped otherwise) | covered |
| MS-9 | Debug bootstrap: with no active model the bundled package is imported then activated through the use cases; nothing otherwise; refusals reported | app → `ModelBootstrapTest`; on device (2026-10-23): first start imported then activated the bundled package, audited, old unsigned layout replaced | covered |
| MS-10 | Overlay labels of the active model cached per version, refreshed after a swap, never read while drawing | app → `ActiveLabelsTest`; labels of a package: store → `FileModelRepositoryTest` | covered |
| MS-11 | Models screen: import from a file, activate, rollback, disabled while `ModelSwapPolicy` refuses; Live ↔ Models navigation with system back | `ModelsModule`, `ModelsRoutes`, `AppNavHost`; screen logic: models:ui tests; on device (2026-10-23) through the system file picker: a tampered package refused (bad signature, audited), a second signed version imported, activated and rolled back, the three actions disabled with an explanation while running | covered |
| MS-12 | Trusted keys loaded from `assets/keys`; a release build with no key refuses every import | `TrustedKeyAssets`; signature rule: `FileModelRepositoryTest`; debug key checked on device (2026-10-23); release build without a key not yet run | covered (release case not yet run) |

## Telemetry transport (M6)

The JVM tests are in `features/telemetry/room/src/test`; the instrumented ones (JUnit 4, `androidTest`) need a device.

| ID | Requirement | Tests | Status |
|---|---|---|---|
| TELA-1 | Capped buffer: oldest events dropped first, in the insert's transaction, dropped events counted | room (instrumented) → `TelemetryDaoTest` (cap, batch larger than the cap, oldest-first paging, delete by ids, ids never reused), `RoomTelemetryBufferTest` (drop count and callback) ; run on device (test phone, Android 16, 2026-10-23) | covered |
| TELA-2 | Batching: 500 events per page, gzipped JSON array, rows deleted only after an accepted upload, failure keeps them and retries, page limit per run | telemetry:domain → `TelemetryBatchTest`; room → `TelemetryBatchBuilderTest` (valid array, order, gzip round trip), `TelemetryUploadRunTest` (pages, failure, throwing uploader, limit); room (instrumented) → `TelemetryUploadWorkerTest` (success deletes, failure keeps and returns retry, empty buffer), run on device (2026-10-23) | covered |
| TELA-3 | Every event type has a JSON encoding `{"type","atMillis",...}`; an unknown family is not encoded | room → `TelemetryEventEncoderTest` (one test per event type, null fields, unknown event) | covered |
| TELA-4 | `record` never blocks or throws: bounded queue dropping its oldest event, counted; a failing buffer loses one batch only; debug tee keeps the Logcat line | room → `RoomTelemetrySinkTest`, `TeeTelemetrySinkTest` | covered |
| TELA-5 | `LoggingUploader` logs count and sizes, the first events in debug, and accepts the batch | room → `LoggingUploaderTest` | covered |
| TELA-6 | Periodic unique work (15 min, network, exponential backoff, KEEP); debug one-time upload; WorkManager configured by the application (default initializer removed) | `TelemetryUploadScheduler`, `FramelockApplication`, merged manifest checked (`WorkManagerInitializer` absent); no automated test; on device (2026-10-23): the work ran at start, uploaded 2 events (102 B gzipped) through `LoggingUploader` and succeeded, then the debug one-time run found the buffer empty | implemented, checked manually |

## Later milestones

| ID | Requirement | Status |
|---|---|---|
| LRT-1 | Real-image detections: each annotated person has a distinct same-class detection with score ≥ 0.5 and IoU ≥ 0.8, independent of order and extras | inference-litert → `LiteRtGoldenTest` (forced CPU/GPU × RGBA/YUV), `DetectionMatchingTest` (assignment and rejection cases); [fixture provenance](../features/live/inference-litert/src/androidTest/assets/classroom-004s.md); needs a device and `scripts/fetch-model.sh` | covered: 10 instrumented tests passed on API 36, 2026-10-28; no generated golden |
| UI-T | `Modifier.track(ElementId)` routes clicks to `TrackUiInteraction` | live:ui → `Track.kt`, wired in `LiveModule`; no automated test yet | implemented, untested |
| PIPE-7b | Trace sections for capture, preprocess, postprocess and render (ADR-009 ownership) | capture-camerax, capture-file, inference-litert, live:ui `FrameRenderer` (`RENDER`: conversion and bitmap upload, M7) (visible in Perfetto; no automated test) | implemented, untested (to check in a trace) |
| LBL-1 | Overlay names come from the labels embedded in the model, whatever the zip offset convention | models:store → `EmbeddedLabelsTest` (both conventions, stored and deflated entries, the fetched model when present) | covered |
| CAP-1 | Camera frames delivered upright and following display rotation, without a new session | capture-camerax → `YuvFramePoolTest` (pool swapped only on a size change, buffers of a replaced pool released to it); rotation checked on device (2026-10-23) | covered (rotation manual) |
| UI-1 | "No signal" banner shown and overlay hidden on disconnect | live:ui → `LiveScreen` shows the `NoSignal` banner (`live_main_state_no_signal`) and the overlay comes from `PipelineUiState.annotated`, which `NoSignal` clears (live:application → `InferencePipelineConnectionTest`); no UI test yet | implemented, untested |
| UI-2 | The video source can be chosen (Auto, back, front, external, each UVC device) while stopped; an explicit choice overrides Auto and applies at the next start; the HUD shows the active source and UVC mode | app → `SelectingFrameSourceTest` (explicit choice, Auto order unchanged, applies after close, description), `SourceSelectionTest` (option listing, id mapping, selection); capture-uvc → `UvcDeviceNamingTest` (labels, ids, HUD text); picker menu seen on device listing Auto, back and front cameras (2026-10-23); a UVC entry not yet seen | covered (UVC entry not yet seen) |
| UI-3 | The live screen is full-bleed with status, HUD and controls as frosted-glass panels overlaid; detections get a per-class colour and a label chip anchored at the box corner; layout follows window insets and adapts to landscape | live:ui → `FrameLayoutTest` (letterbox fit, chip placement), designsystem → `OverlayPaletteTest` (deterministic class colour, chip text AA), `ContrastTest` (status colours AA with the dark chip text), `GlassPaletteTest` (glass text, Stop label, accent button and detection label chip AA over a white picture); the Compose layout itself has no automated test | implemented, layout untested (to check on device) |
| PIPE-6b | No per-frame allocation (measured) | capture pools: one breach found by reading code and removed (opt3, `PlaneCopierTest` checks buffers are reused); no allocation profile recorded yet | partly covered |
| PERF-1 | Baseline tooling: a fixed input video, a scripted 10-minute run with a cool-down gate, a Perfetto trace, CPU/PSS/temperature readings, a summary and a results table (architecture.md "Performance method") | `scripts/perf/` (`fetch-input.sh`, `run.sh`, `summarize.py`; `test_summarize.py`: doctests and a fixture run directory), `docs/perf/README.md`, `docs/perf/results.md`; app side: `DebugLaunch` (`DebugLaunchTest`), backend override and HUD line; no run on a device yet, no baseline recorded | tooling covered (Python self-test), not yet run on a device |
| PERF-2 | Inference backend is reported (log `LiteRtEngine`, HUD) and can be forced in debug builds, a forced GPU never falls back silently (ADR-005 amendment) | inference-litert `LiteRtInferenceEngine` (`BackendPreference`); no automated test (needs a device) | implemented, untested |
| PERF-3 | Render converts only the on-screen rectangle, same colours, strides honoured (opt1) | live:ui → `I420ToArgbTest`, `RgbaToArgbTest`; measured: RENDER p50 360 → 9 ms (`docs/perf/results.md`) | covered |
| PERF-4 | Preprocessing gives a bit-identical model input with cached tables and bulk reads (opt2) | inference-litert → `PreprocessReferenceTest`, `YuvToRgbTest`, `RgbaToRgbTest`; measured: PREPROCESS p50 39 → 2 ms | covered |
| PERF-5 | Capture copies planes without per-row allocation, output identical (opt3) | capture-camerax, capture-file → `PlaneCopierTest`, pool tests; measured: CAPTURE p50 76 → 6 ms, input 11 → 30 fps | covered |
| PERF-6 | Results table: before/after per change, same input and conditions | `docs/perf/results.md` (baseline and three optimisations, 2026-10-24) | covered |
| PERF-7 | GPU delegate versus CPU (XNNPACK) measured under the same protocol, with delegate coverage, thread load and published app-only traces | `docs/perf/gpu-vs-cpu.md`, runs `backend-gpu` and `backend-cpu` (2026-10-26), `scripts/perf/timeline.py`, `run.sh --public` | covered |
| SRC-F1 | `VideoFileFrameSource`: plays an H.264 MP4 in a loop at the file's rate, `Connected` on the first frame, I420 pool of 4 (skip when empty, release once), arrival timestamps, own decoder thread, full release on `close()`, `OPEN_FAILED` / `STREAM_ERROR` (ADR-014 amendment) | capture-file → `PlaybackScheduleTest` (due times from presentation timestamps, late frame, stall re-anchor, seamless loop), `I420FramePoolTest` (tight copy, skip when empty, reuse, double close); the decoder itself has no automated test (no licence-clean clip bundled; to check on a device) | pacing and pool covered, decoder untested |
| SRC-F2 | "Test video" source offered in debug builds only, when `filesDir/perf/input.mp4` exists; never chosen by Auto; refused in a build without it | app → `SourceSelectionTest`, `SelectingFrameSourceTest` | covered |
| SRC-F3 | Debug launch extras (`source`, `autostart`, `hud`, `backend`) drive a run without taps; ignored in release builds | app → `DebugLaunchTest` (parsing); wiring in `MainActivity` / `DebugLaunchHandler` has no automated test | parsing covered, wiring untested |
