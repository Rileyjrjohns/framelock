# Framelock

Real-time on-device inference on a live video feed, on Android. Frames come from a USB/UVC capture card
(HDMI → USB) or from the device camera; a detection model runs on each analysed frame, and the result is
drawn on the exact frame it was computed from.

> Status: **M0, skeleton.** Modules, build conventions and architecture checks are in place; no feature yet.

## Requirements

- JDK 17
- Android SDK with platform 37 (Android Gradle Plugin downloads it on first build if licences are accepted)
- An Android 10+ device (minSdk 29). For the capture card path: USB-C with OTG and an HDMI → USB UVC dongle.

## Build and test

```bash
./gradlew check          # unit tests, architecture rules, module boundaries, ktlint, detekt, Lint
./gradlew assembleDebug  # app/build/outputs/apk/debug/
```

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
