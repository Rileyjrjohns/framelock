# Framelock

Real-time on-device inference on a live video feed, on Android. Frames come from a USB/UVC capture card
(HDMI → USB) or from the device camera; a detection model runs on each analysed frame, and the result is
drawn on the exact frame it was computed from.

> Status: **M4, system adapters.** Thermal (status listener plus forecast headroom on Android 11+) and memory pressure (trim callbacks plus `ActivityManager.MemoryInfo` polling) now drive the degradation of ADR-013 ([ADR-017](docs/adr/ADR-017-system-adapters.md)); the HUD shows both.
>
> Before that, **M3, capture card.** The source is chosen when a session starts: a camera exposed as external by Android, else an attached USB video class device (capture card, through UVCAndroid), else the back camera. LiteRT inference (GPU delegate, CPU fallback) and the live screen with its synchronized overlay and debug HUD as in M2.

## Requirements

- JDK 17
- Android SDK with platform 37 (Android Gradle Plugin downloads it on first build if licences are accepted)
- An Android 10+ device (minSdk 29). For the capture card path: USB-C with OTG and an HDMI → USB UVC dongle.

## Run

```bash
./scripts/fetch-model.sh                 # downloads the public detection model (checked by SHA-256)
./gradlew installDebug                   # on a connected Android 10+ device
```

Open the app, allow the camera, tap **Start**. The status banner says what the pipeline does; **Debug HUD** shows
input and inference fps, inference and end-to-end latency, and dropped frames.

### With a capture card

Plug the card in before tapping **Start**; Android asks once for the USB permission. The source is chosen at start:
stop and start again after plugging or unplugging. When the card stops sending frames for 2 s the banner shows *No
signal* and the app retries. The chosen capture mode (format, size, rate) is logged once per stream:
`adb logcat -s UvcFrameSource`.

A phone usually has a single USB-C port, which the card takes, so `adb` over USB is not available. Use wireless
debugging instead: *Developer options → Wireless debugging → Pair device with pairing code*, then
`adb pair <host:port>` and `adb connect <host:port>`; `./gradlew installDebug` and `adb logcat` work over that link.

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

(`dev.framelock` is the application id). The command accepts `HIDDEN`, `RUNNING_MODERATE`, `BACKGROUND`, `RUNNING_LOW`, `MODERATE`,
`RUNNING_CRITICAL`, `COMPLETE` or a raw integer; the level must be higher than the
current one (so the second call may be refused until the process is restarted) and a background level is refused on a
foreground process. The source suggests that on Android 14+ the shell still hands the level to the app, although the
system no longer sends the `RUNNING_*` levels itself; this is not checked on a device. If nothing happens there, rely
on the real signal instead: fill memory with another app and watch `Memory` in the HUD.

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
| `features/telemetry` | Metrics-only telemetry: `domain` (allowlist), `application` (recorder), `room`. |
| `app` | Composition root (manual DI). |
| `architecture-tests` | Konsist rules. |
| `build-logic` | Gradle convention plugins and the module boundary check. |

Each feature keeps its hexagonal layers as separate modules; `domain` and `application` are pure Kotlin
([ADR-014](docs/adr/ADR-014-feature-first-modules.md)).

Design: [docs/architecture.md](docs/architecture.md). Decisions: [docs/adr/](docs/adr/).
Requirement-to-test mapping: [docs/traceability.md](docs/traceability.md).
Third-party software: [docs/soup.md](docs/soup.md).
