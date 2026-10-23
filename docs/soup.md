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

### Added in M3 (shipped)

| Component | Version | Purpose | Licence |
|---|---|---|---|
| UVCAndroid (`com.herohan:UVCAndroid`) | 1.0.13 (latest stable, released 2026-06-10; verified on Maven Central 2026-10-23) | USB video class source for the capture card (low-level `USBMonitor` and `UVCCamera`, ADR-016) | Apache-2.0 (POM) |

Bundled native libraries of UVCAndroid 1.0.13, for arm64-v8a, armeabi-v7a, x86 and x86_64 (the 64-bit ones are 16 KB
aligned; `zipalign -c -P 16` passes on the debug APK):

| Library | Licence | Note |
|---|---|---|
| libUVCCamera (JNI part of UVCAndroid) | Apache-2.0 | |
| libuvc | BSD | |
| libusb 1.0 | LGPL-2.1 | Shipped as a shared object (`libusb1.0.so`), not statically linked |
| libjpeg-turbo | BSD-style (IJG, Modified BSD, zlib) | MJPEG decoding |
| libyuv | BSD | Pixel format conversion |

Their licences come from the library's source tree and are not carried by the POM.

Transitive dependencies of UVCAndroid 1.0.13 (declared version, resolved version in the app):

| Component | Declared | Resolved | Licence |
|---|---|---|---|
| `androidx.core:core` | 1.13.1 | 1.19.1 | Apache-2.0 |
| `androidx.legacy:legacy-support-v4` (with core-utils, core-ui, fragment, media, print, loader and related support libraries) | 1.0.0 | 1.0.0 | Apache-2.0 |
| `androidx.appcompat:appcompat` | 1.6.1 | 1.6.1 | Apache-2.0 |
| `androidx.annotation:annotation` | 1.8.0 | 1.10.0 | Apache-2.0 |
| `cn.hutool:hutool-core` | 5.8.35 | 5.8.35 | Not verified: its POM declares no licence. The project is published under Mulan PSL v2, which is to be confirmed from its repository before a release |

The AAR's manifest also declares `RECORD_AUDIO`, `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`,
`WRITE_EXTERNAL_STORAGE` and `FOREGROUND_SERVICE`; the app removes them from the merged manifest. Open library issues
that matter here are listed in ADR-016.

### Model (downloaded, not committed)

| Component | Version | Source | Licence |
|---|---|---|---|
| EfficientDet-Lite0 with the TFLite detection post-processing op and embedded COCO labels | Kaggle Models `tensorflow/efficientdet/tfLite/lite0-detection-metadata` v1, SHA-256 `2e04c53b…4e0b` | `scripts/fetch-model.sh` (Kaggle API download) | Apache-2.0 (Kaggle model page); trained on COCO 2017 (annotations CC BY 4.0, images under Flickr terms) |

### Added in M6 (shipped)

Versions checked on 2026-10-23: Room 2.8.5 and WorkManager 2.12.0 are the latest stable in Google Maven
(`dl.google.com/dl/android/maven2/androidx/{room/room-runtime,work/work-runtime}/maven-metadata.xml`), KSP 2.3.12 and
kotlinx-serialization-json 1.12.0 the latest on Maven Central; licences read from each POM.

| Component | Version | Purpose | Licence |
|---|---|---|---|
| AndroidX Room (`room-runtime`, with `room-common`) | 2.8.5 | Capped telemetry buffer (ADR-004, M6) | Apache-2.0 |
| AndroidX SQLite (`sqlite`, `sqlite-framework`) | 2.6.2 (transitive of Room) | Database access | Apache-2.0 |
| AndroidX WorkManager (`work-runtime`) | 2.12.0 | Periodic telemetry upload | Apache-2.0 |
| kotlinx-serialization-json (with `kotlinx-serialization-core`) | 1.12.0 (core raised from 1.7.3 by this dependency) | JSON encoding of telemetry events, written by hand, in the adapter only | Apache-2.0 |

## Tests only

| Component | Version | Purpose | Licence |
|---|---|---|---|
| JUnit Jupiter / Platform (JUnit 6) | 6.1.3 | JVM and local Android tests | EPL-2.0 |
| kotlinx-coroutines-test | 1.11.0 | Virtual time in tests | Apache-2.0 |
| Konsist | 0.18.1 | Architecture rules | Apache-2.0 |
| JUnit 4 | 4.13.2 | Instrumented tests | EPL-1.0 |
| AndroidX Test (core, runner, rules, ext-junit) | 1.7.0 / ext 1.3.0 | Instrumented tests | Apache-2.0 |
| AndroidX WorkManager testing (`work-testing`) | 2.12.0 | `TestListenableWorkerBuilder` for the upload worker (instrumented) | Apache-2.0 |
| Compose UI test (junit4, manifest), UI tooling | BOM 2026.09.00 | UI tests, debug tooling | Apache-2.0 |

## Build tools

| Component | Version | Purpose | Licence |
|---|---|---|---|
| Gradle | 9.8.2 | Build | Apache-2.0 |
| Android Gradle Plugin | 9.4.1 | Android build, built-in Kotlin, Lint | Apache-2.0 |
| Kotlin Gradle Plugin, Compose compiler plugin | 2.4.21 | Kotlin compilation | Apache-2.0 |
| KSP | 2.3.12 | Annotation processing (Room compiler, M6; works with AGP 9.4.1 built-in Kotlin, see the build) | Apache-2.0 |
| Room Gradle plugin (`androidx.room`) | 2.8.5 | Exports the database schema (`features/telemetry/room/schemas/`) | Apache-2.0 |
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
| Benchmark, Baseline Profile plugin, ProfileInstaller (direct; 1.4.0 already ships transitively), UI Automator | 1.5.0 / 1.4.1 / 2.4.0 | M7 | Apache-2.0 |
