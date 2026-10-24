# Framelock

Real-time on-device inference on a live video feed, on Android. Frames come from a USB/UVC capture card
(HDMI → USB) or from the device camera; a detection model runs on each analysed frame, and the result is
drawn on the exact frame it was computed from.

> Status: **M7, performance pass: three measured optimisations.** End-to-end latency 1.13 s → 119 ms and 2.6 → 14.3
> analyses per second on a fixed 1080p input: see [docs/perf/results.md](docs/perf/results.md). Release APK on tag: pending.
>
> Before that, **M6, telemetry.** Accepted events are stored in a capped Room table and uploaded in gzipped JSON batches by WorkManager; the default uploader only logs ([ADR-004](docs/adr/ADR-004-typed-telemetry.md), amendment M6).
>
> Before that, **M5, model store.** Models are imported from signed `.flmodel` packages (ECDSA P-256 over a manifest that covers the model and its labels), activated and rolled back from a Models screen, with an audit trail ([ADR-003](docs/adr/ADR-003-signed-models-outside-apk.md), amendment M5).
>
> Before that, **M4, system adapters.** Thermal (status listener plus forecast headroom on Android 11+) and memory pressure (trim callbacks plus `ActivityManager.MemoryInfo` polling) now drive the degradation of ADR-013 ([ADR-017](docs/adr/ADR-017-system-adapters.md)); the HUD shows both.
>
> Before that, **M3, capture card.** The source is chosen when a session starts: a camera exposed as external by Android, else an attached USB video class device (capture card, through UVCAndroid), else the back camera. LiteRT inference (GPU delegate, CPU fallback) and the live screen with its synchronized overlay and debug HUD as in M2.

## Requirements

- JDK 17
- Android SDK with platform 37 (Android Gradle Plugin downloads it on first build if licences are accepted)
- An Android 10+ device (minSdk 29). For the capture card path: USB-C with OTG and an HDMI → USB UVC dongle.

## Run

```bash
./scripts/fetch-model.sh                 # downloads the public detection model (checked by SHA-256), signs it for debug builds
./gradlew installDebug                   # on a connected Android 10+ device
```

The debug build bundles the signed model and a development public key; at first start it imports and activates the
model like any other import. Open the app, allow the camera, tap **Start**. The status banner says what the pipeline does; **Debug HUD** shows
input and inference fps, inference and end-to-end latency, and dropped frames.

### Models

A model is one `.flmodel` file (a zip of `model.tflite`, `manifest.properties`, `manifest.sig` and optionally
`labels.txt`). The app imports only packages whose manifest is signed by a key it trusts, and checks the SHA-256 of
the model and labels against the signed manifest. Keys are kept outside the repository.

```bash
# once: an ECDSA P-256 key pair in ~/.framelock/keys/ (never overwritten); keep release.pem private
./scripts/sign-model.sh --new-key
# sign a model (tensor specs are <dims joined by x>:<FLOAT32|UINT8|INT8>, outputs separated by ;)
./scripts/sign-model.sh --model my.tflite --version my-model-1 --input 1x320x320x3:UINT8 \
  --outputs '1x25x4:FLOAT32;1x25:FLOAT32;1x25:FLOAT32;1:FLOAT32' --key ~/.framelock/keys/release.pem \
  --labels labels.txt --out my-model-1.flmodel          # --labels is optional: else the labels embedded in the model are used
# put it on the device, then in the app: Models -> Import, pick the file, then Activate
adb push my-model-1.flmodel /sdcard/Download/
```

The app trusts every `*.pub.pem` file in its `assets/keys/`. The debug build trusts the development key written by
`scripts/fetch-model.sh` (`app/src/debug/assets/keys/`, not committed). **No release key exists yet**: to ship one,
create it with `--new-key` and copy its `release.pub.pem` into `app/src/main/assets/keys/` (public keys can be committed);
until then a release build refuses every import. Importing and swapping are only possible while no session runs
(the Models screen disables them otherwise). *Roll back* returns to the previous version, which stays on disk; the audit
trail is `filesDir/audit/audit.log` (`adb shell run-as dev.framelock cat files/audit/audit.log` on a debug build).

### With a capture card

Plug the card in before tapping **Start**; Android asks once for the USB permission. The source is chosen at start:
stop and start again after plugging or unplugging. When the card stops sending frames for 2 s the banner shows *No
signal* and the app retries. The chosen capture mode (format, size, rate) is logged once per stream:
`adb logcat -s UvcFrameSource`.

A phone usually has a single USB-C port, which the card takes, so `adb` over USB is not available. Use wireless
debugging instead: *Developer options → Wireless debugging → Pair device with pairing code*, then
`adb pair <host:port>` and `adb connect <host:port>`; `./gradlew installDebug` and `adb logcat` work over that link.

### Performance runs (debug)

A debug build can play a fixed video in a loop instead of a camera and be driven by a script, to take repeatable
measurements (inference and end-to-end latency, CPU, memory, temperature, a Perfetto trace):

```bash
./scripts/perf/fetch-input.sh            # downloads the video, checks its SHA-256, pushes it to the debug app
./scripts/perf/run.sh baseline-gpu 600   # one 10-minute run; writes docs/perf/runs/baseline-gpu/ and a row in docs/perf/results.md
```

Details, conditions to hold constant and how to read a trace: [docs/perf/README.md](docs/perf/README.md).

### Degradation (debug)

With a session running and **Debug HUD** on (it shows `Thermal: … · memory: …`), force a thermal status; the banner
follows the ADR-013 table:

```bash
adb shell cmd thermalservice override-status 3   # 0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown
adb shell cmd thermalservice reset               # unlock the real status
```

| Status | Banner |
|---|---|
| 0 none, 1 light | Running |
| 2 moderate | Degraded: thermal, 15 fps |
| 3 severe | Degraded: thermal, 10 fps |
| 4 critical | Degraded: thermal, 5 fps |
| 5 emergency, 6 shutdown | Paused: thermal |

`override-status` and `reset` are in the `thermalservice` help of the platform source. The override replaces the
status that the listener reports; the forecast headroom is not overridden, so at the same time a hot device can show
a worse level than the one forced. Memory:

```bash
adb shell am send-trim-memory dev.framelock RUNNING_LOW        # banner: Degraded: memory, 15 fps, for 30 s
adb shell am send-trim-memory dev.framelock RUNNING_CRITICAL   # banner: Paused: memory, for 30 s
```

(`dev.framelock` is the application id). The command accepts `HIDDEN`, `RUNNING_MODERATE`, `BACKGROUND`,
`RUNNING_LOW`, `MODERATE`, `RUNNING_CRITICAL`, `COMPLETE` or a raw integer; the level must be higher than the current
one (so the second call may be refused until the process is restarted) and a background level is refused on a
foreground process. On Android 14+ the shell still hands the level to the app (checked on Android 16), although the
system no longer sends the `RUNNING_*` levels itself.

### Telemetry (debug)

Telemetry is metrics only (typed events, no images, no free text). Events are queued, written to a Room table
(`databases/telemetry.db`, at most 10 000 rows, oldest dropped first) and uploaded every 15 minutes while a network is
available. The only uploader is `LoggingUploader`: it sends nothing and logs. A debug build also logs each event and
runs one upload 20 s after start, so a check does not wait 15 minutes:

```bash
adb logcat -c
adb logcat -s Telemetry TelemetryBuffer TelemetryUpload WM-WorkerWrapper
# Telemetry        every recorded event (debug only), as a Kotlin object, not as stored JSON
# TelemetryUpload  "Upload of N events: X B gzipped, Y B raw" then the first five events as stored JSON
# TelemetryBuffer  "Buffer full: ..." when the cap dropped events, "Telemetry queue full" / "write failed" on overload
```

Start the app, use it for a while, then wait 20 s: the upload line appears (the work needs a network; wireless
debugging implies one). The rows are deleted after the upload, so the table is usually empty right after. To see the
work itself: `adb shell dumpsys jobscheduler | grep -B2 -A12 dev.framelock` lists the scheduled jobs, and on a debug build
`adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p dev.framelock` prints the recent, running
and scheduled work to Logcat (`WM-DiagnosticsWrkr`). To read the buffer, copy the database (with its `-wal` file) out
of the debug app:

```bash
adb exec-out run-as dev.framelock cat databases/telemetry.db > telemetry.db
adb exec-out run-as dev.framelock cat databases/telemetry.db-wal > telemetry.db-wal
sqlite3 telemetry.db 'select id, payload from telemetry_events order by id limit 10'
```

Both the periodic work and the debug one-time work are unique (`telemetry-upload`, `telemetry-upload-once`). An
`HttpUploader` is not implemented yet.

## Build and test

```bash
./gradlew check                          # unit tests, architecture rules, module boundaries, ktlint, detekt, Lint
./gradlew assembleDebug                  # app/build/outputs/apk/debug/
./gradlew connectedDebugAndroidTest      # instrumented tests, including the LiteRT golden test (device needed)
```

End-to-end latency is render time minus capture time; it does not include compositor and display latency.

## Layout

| Path | Content |
|---|---|
| `shared/kernel` | Shared vocabulary: clock, tracing, model descriptors, telemetry event types. Pure Kotlin. |
| `shared/testing` | A fake for every port, shared by all tests. |
| `features/live` | Capture, inference and display: `domain`, `application` (pipeline, metrics), adapters (UVC, CameraX, LiteRT, system), `ui`. |
| `features/models` | Model import, signature check, activation and rollback: `domain`, `application`, `store`, `ui`. |
| `features/telemetry` | Metrics-only telemetry: `domain` (allowlist), `application` (recorder), `room` (capped buffer, batch upload). |
| `app` | Composition root (manual DI). |
| `architecture-tests` | Konsist rules. |
| `build-logic` | Gradle convention plugins and the module boundary check. |

Each feature keeps its hexagonal layers as separate modules; `domain` and `application` are pure Kotlin
([ADR-014](docs/adr/ADR-014-feature-first-modules.md)).

Design: [docs/architecture.md](docs/architecture.md). Decisions: [docs/adr/](docs/adr/).
Requirement-to-test mapping: [docs/traceability.md](docs/traceability.md).
Third-party software: [docs/soup.md](docs/soup.md).
