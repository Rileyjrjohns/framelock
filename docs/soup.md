# Third-party software

Every third-party component in the build, with its version, purpose and licence. Direct dependencies come from
`gradle/libs.versions.toml`; a component is listed when a module starts using it. Transitive components shipped in
the APK are listed from `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and re-checked at
every milestone. Licences are read from each artifact's POM.

## Shipped in the APK

| Component | Version | Purpose | Licence |
|---|---|---|---|
| Kotlin standard library | 2.4.21 | Language runtime | Apache-2.0 |
| kotlinx-coroutines-core | 1.11.0 | Coroutines and Flow (the only library allowed in pure modules) | Apache-2.0 |
| AndroidX Core | 1.19.1 | Android compatibility APIs | Apache-2.0 |
| Jetpack Compose (BOM 2026.09.00: ui 1.12.1, foundation, material3 1.4.0) | BOM 2026.09.00 | UI | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | Compose entry point | Apache-2.0 |
| AndroidX Lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | Lifecycle-aware state, ViewModels | Apache-2.0 |

### Transitive, shipped in the APK (M0)

| Group | Versions | Licence |
|---|---|---|
| `androidx.*`: activity, annotation, arch.core, autofill, collection, compose (animation, foundation, material-ripple, runtime, ui), concurrent-futures, core-viewtree, customview-poolingcontainer, emoji2 1.4.0, graphics-path, interpolator, lifecycle (common, livedata-core, process, runtime, viewmodel, savedstate), navigationevent 1.0.0, profileinstaller 1.4.0, savedstate 1.4.0, startup-runtime 1.1.1, tracing 1.2.0, versionedparcelable, window 1.5.0 | as resolved | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` (via Compose) | 1.7.3 | Apache-2.0 |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 |


### Added in M2 (shipped)

| Component | Version | Purpose | Licence |
|---|---|---|---|
| CameraX (core, camera2, lifecycle) | 1.6.2 | Fallback video source | Apache-2.0; `camera-camera2-pipe` brings Dagger 2.59 (Apache-2.0) at runtime, used internally by CameraX |
| AndroidX Lifecycle runtime | 2.11.0 | Lifecycle owned by the CameraX source (CameraX declares 2.3.x) | Apache-2.0 |
| LiteRT (`litert`, `litert-gpu`, with `litert-api`, `litert-gpu-api`) | 1.4.2 | On-device inference, GPU delegate | Apache-2.0 |
| AndroidX Tracing | 2.0.5 | Trace sections for Perfetto | Apache-2.0 |
| kotlinx-coroutines-android | 1.11.0 | Main dispatcher | Apache-2.0 |

### Model (downloaded, not committed)

| Component | Version | Source | Licence |
|---|---|---|---|
| EfficientDet-Lite0 int8, with embedded COCO labels | MediaPipe object detector v1, SHA-256 `0720bf24…23bb` | `scripts/fetch-model.sh` (storage.googleapis.com/mediapipe-models) | Apache-2.0 (per the model's publisher; to confirm against its model card before any distribution); trained on COCO 2017 (annotations CC BY 4.0, images under Flickr terms) |

## Tests only

| Component | Version | Purpose | Licence |
|---|---|---|---|
| JUnit Jupiter / Platform (JUnit 6) | 6.1.3 | JVM and local Android tests | EPL-2.0 |
| kotlinx-coroutines-test | 1.11.0 | Virtual time in tests | Apache-2.0 |
| Konsist | 0.18.1 | Architecture rules | Apache-2.0 |
| JUnit 4 | 4.13.2 | Instrumented tests | EPL-1.0 |
| AndroidX Test (core, runner, rules, ext-junit) | 1.7.0 / ext 1.3.0 | Instrumented tests | Apache-2.0 |
| Compose UI test (junit4, manifest), UI tooling | BOM 2026.09.00 | UI tests, debug tooling | Apache-2.0 |

## Build tools

| Component | Version | Purpose | Licence |
|---|---|---|---|
| Gradle | 9.8.2 | Build | Apache-2.0 |
| Android Gradle Plugin | 9.4.1 | Android build, built-in Kotlin, Lint | Apache-2.0 |
| Kotlin Gradle Plugin, Compose compiler plugin | 2.4.21 | Kotlin compilation | Apache-2.0 |
| KSP | 2.3.12 | Annotation processing (Room, from M6) | Apache-2.0 |
| Foojay toolchain resolver | 1.0.0 | JDK provisioning | Apache-2.0 |
| Spotless | 8.10.4 | Formatting check | Apache-2.0 |
| ktlint | 1.8.0 | Kotlin style | MIT |
| Compose rules for ktlint (`io.nlopez.compose.rules`) | 0.6.8 | Compose lint rules | Apache-2.0 |
| detekt | 1.23.8 | Static analysis of pure modules | Apache-2.0 |
| GitHub Actions: checkout v7, setup-java v6, upload-artifact v7 | — | CI | MIT |
| GitHub Action: gradle/actions/setup-gradle v6 | — | CI, wrapper validation, cache | MIT; caching component under the Gradle Terms of Use |

## Planned

Recorded with their milestone so licences are reviewed before code depends on them.

| Component | Version | Milestone | Licence |
|---|---|---|---|
| UVCAndroid (`com.herohan:UVCAndroid`) | 1.0.13 | M3 | Apache-2.0; bundles libusb (LGPL-2.1), libuvc (BSD), libjpeg-turbo and libyuv (BSD); transitive hutool-core (Mulan PSL v2) |
| kotlinx-serialization-json | 1.12.0 | M5/M6 | Apache-2.0 |
| Room | 2.8.5 | M6 | Apache-2.0 |
| WorkManager | 2.12.0 | M6 | Apache-2.0 |
| Benchmark, Baseline Profile plugin, ProfileInstaller (direct; 1.4.0 already ships transitively), UI Automator | 1.5.0 / 1.4.1 / 2.4.0 | M7 | Apache-2.0 |
