# ADR-014: Feature-first modules, one module per layer

- Status: Accepted
- Date: 2026-10-23
- Supersedes: the layer-first module list of architecture.md ("Modules"); amends ADR-006

## Context

The first layout grouped code by layer (`core/domain`, `core/application`, `adapters/*`). Everything about one
capability (say, model management) was spread over four places, and nothing stopped one capability's domain from
leaning on another's. The team works feature-first.

## Decision

Modules are grouped by feature, and each feature keeps the hexagonal layers as separate modules:

```
:shared:kernel                  pure Kotlin — shared vocabulary: Clock, Tracer, model descriptors (ModelVersion,
                                ModelManifest, ModelArtifact), TelemetryEvent (open interface), ScreenId, ElementId
:shared:testing                 pure Kotlin — a fake for every port
:features:live:domain           pure Kotlin — Frame, InferenceResult, PipelineState, policies, ports
                                (FrameSource, InferenceEngine, ThermalMonitor, MemoryPressureMonitor, DeviceInfoProvider)
:features:live:application      pure Kotlin — InferencePipeline, MetricsAggregator, Start/Stop/Observe/ReportFrameRendered
:features:live:capture-uvc      adapter
:features:live:capture-camerax  adapter
:features:live:capture-file     adapter (debug/benchmark FrameSource, M7)
:features:live:inference-litert adapter
:features:live:system           adapter (thermal, memory, device info, tracing, clock)
:features:live:ui               Compose live screen and HUD
:features:models:domain         pure Kotlin — ModelSource, ModelSwapPolicy, ports ModelRepository, AuditLog
:features:models:application    pure Kotlin — Import/Activate/Rollback
:features:models:store          adapter
:features:models:ui             Compose models screen
:features:telemetry:domain      pure Kotlin — TelemetryAllowlist, port TelemetrySink
:features:telemetry:application pure Kotlin — TelemetryRecorder, TrackScreen, TrackUiInteraction
:features:telemetry:room        adapter
:app                            composition root (manual DI)
:architecture-tests             Konsist
```

### Features depend on each other in one direction

`models → live`, `telemetry → live, models`. Never the reverse, so the feature graph has no cycle. Each feature
defines its own telemetry events (`LiveTelemetry`, `ModelTelemetry`, sealed, implementing the kernel's
`TelemetryEvent`); the telemetry feature, downstream of all, checks them with an exhaustive allowlist and rejects any
other subtype. Producers emit through a `(TelemetryEvent) -> Unit` function wired in `:app`, so no feature depends on
the telemetry feature.

### Layers depend inward

| Layer | May reach (production) |
|---|---|
| `shared:kernel` | nothing |
| `domain` | kernel, domains of upstream features |
| `application` | kernel, its domain, domains and applications of upstream features |
| adapter | kernel, its domain, domains of upstream features |
| `ui` | kernel, its domain and application, those of upstream features |
| `shared:testing` | kernel, every domain |
| `app` | everything except `shared:testing` and `architecture-tests` |

Test classpaths may add `:shared:testing` and what it reaches. Pure-Kotlin modules (`kernel`, `testing`, every
`domain` and `application`) keep the rules of ADR-006: no `android.*`, no DI, kotlinx-coroutines-core only.

Packages follow modules: `dev.framelock.kernel`, `dev.framelock.<feature>.domain`, `dev.framelock.<feature>.application`,
`dev.framelock.<feature>.<adapter>`, `dev.framelock.<feature>.ui`. Ports live in `dev.framelock.<feature>.domain.port`
or `dev.framelock.kernel.port`.

## Consequences

- One capability is read in one place; the feature graph is checked by Gradle like the layers (ADR-006).
- More modules (18), each small. Convention plugins keep their build files to a few lines.
- `ModelSwapPolicy` sits in `models:domain` and reads the live feature's `PipelineState`: allowed, since models is
  downstream of live.
- `architecture.md` "Modules" and ADR-006 describe this layout from now on.

### Amendment (2026-10-24, M7): capture-file, a debug/benchmark FrameSource

The performance pass needs the same pixels at the same rate on every run, which no camera gives. A new adapter,
`:features:live:capture-file` (`dev.framelock.live.capture.file`), implements `FrameSource` over an H.264 MP4 played in
real time and in a loop (`MediaExtractor` + `MediaCodec`). It is an adapter like the others: it reaches the kernel and
`live:domain` only, so the reach table above is unchanged (`ModuleRoleTest` pins the new row). It keeps a minimal copy of
the I420 pool of `capture-camerax` rather than depending on it: adapters never depend on each other.

`:app` offers it as a "Test video" source in debug builds only, when `filesDir/perf/input.mp4` exists, and never
chooses it automatically. The module is part of the release build's classpath (the dependency is the same for every
variant); a release build never instantiates the source, offers no such option and ignores the debug launch extras
(`DebugLaunch`). Splitting it into a debug-only dependency would need a debug source set for `AppGraph`, which was not
worth it for one class.
