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
- Since ADR-014, `TelemetryEvent` is an open interface and each feature has a sealed family (`UsageTelemetry`,
  `LiveTelemetry`, `ModelTelemetry`); the allowlist rejects any other type (`UNKNOWN_EVENT`). A Konsist rule checks
  that event fields are primitives, project enums or value classes, or one of the bounded strings above.
- `PipelineDegraded(ENGINE_ERROR)` is sent at most once per session, so an intermittent engine fault cannot produce
  an event per frame. Telemetry callbacks are guarded: a failing sink never affects the pipeline.

### Amendment (2026-10-23, M6)

- **Encoding.** `TelemetryEventEncoder` (room adapter) writes each accepted event by hand with kotlinx-serialization-json's
  `buildJsonObject`, as `{"type":"<EventName>","atMillis":<wall clock>, ...fields}`. The event classes live in pure
  modules and carry no annotations. Numbers stay numbers, enums and value classes become their name or value, a missing
  latency or an absent previous model is `null`. Each family's `when` has no `else`, so a new event does not compile until
  it is encoded. An event of an unknown family is not stored (the allowlist already rejects it); the sink counts and logs
  it. Example line:
  `{"type":"PipelineWindow","atMillis":1761000000000,"inferenceMsP50":10.5,"inferenceMsP95":20.0,"e2eMsP50":30.25,"e2eMsP95":60.0,"inputFps":29.9,"inferenceFps":24.5,"droppedFrames":3}`.
- **Buffer.** A Room table `telemetry_events` (autoincrement id, `recordedAtMillis`, `payload` JSON text), schema exported to
  `features/telemetry/room/schemas/`. The cap is 10 000 rows, enforced in the same transaction as the insert: insert, then
  delete the oldest rows beyond the cap. Dropped rows are counted (`RoomTelemetryBuffer.droppedByCap`) and logged (tag
  `TelemetryBuffer`). Ids are never reused, so id order is age order.
- **Sink threading.** `RoomTelemetrySink.record` runs on the main thread and the inference thread and never blocks: it
  stamps the wall clock and `trySend`s into a bounded channel (1 024 events, oldest dropped on overflow, counted and
  logged). One writer coroutine on `Dispatchers.IO` takes up to 64 events at a time, encodes them and appends them. A
  failed write loses that batch (counted, logged); it never reaches the caller. Debug builds tee the sink with
  `LogcatTelemetrySink`, so each event is also logged under the tag `Telemetry`.
- **Upload.** The port `TelemetryUploader` (`suspend fun upload(batch: TelemetryBatch): Boolean`) is in the telemetry
  domain; a `TelemetryBatch` is the event count, the raw size and the gzipped JSON array, nothing platform-specific.
  `TelemetryUploadRun` reads the 500 oldest rows, builds the array, uploads it, and deletes those rows only when the
  uploader returned true; it repeats until the buffer is empty or 20 batches went out (the next run continues). A
  failure, or an uploader that throws, keeps its rows and ends the run; the worker then returns `Result.retry()`.
- **Schedule.** Unique periodic work `telemetry-upload` every 15 minutes (WorkManager's minimum,
  `ExistingPeriodicWorkPolicy.KEEP`), constraint `NetworkType.CONNECTED`, exponential backoff from 30 s. Debug builds also
  enqueue the unique one-time work `telemetry-upload-once` 20 s after start (plain, not expedited: on API 29 and 30 an
  expedited worker needs a foreground notification). It has the same network constraint.
- **WorkManager initialization.** Manual DI: the default initializer is removed in the manifest (only the
  `androidx.work.WorkManagerInitializer` meta-data of `androidx.startup`, since lifecycle and emoji2 use App Startup too)
  and `FramelockApplication` implements `Configuration.Provider` with a `WorkerFactory` built from `AppGraph`
  ([custom configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)).
  The graph never calls WorkManager from its constructor: the configuration provider reads the graph.
- **`LoggingUploader`** is the only uploader: it sends nothing and logs the event count, raw and gzipped sizes (tag
  `TelemetryUpload`) and, in debug builds, the first five events. **`HttpUploader` is not implemented in M6.** It stays
  future work behind a configuration flag; it needs an endpoint, authentication and a retry/idempotency decision that
  belong to its own ADR.
- Not decided here: how long a device may keep events when it never has a network (the cap bounds the storage, the
  oldest events are lost first), and whether the buffer is cleared when the user opts out (there is no opt-out screen).

