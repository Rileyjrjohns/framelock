# Device validation — 2026-10-28

Device: Android 16 / API 36, physical handset, 1080 × 2400. Started from `e20ff9d`; installed the debug update with
existing application data intact. No data clear, performance script or performance measurement was run.

## Results

| Check | Result |
|---|---|
| Container startup and existing model store | Passed; app launches, existing active and previous models retained |
| Back camera, Start/Stop, HUD | Passed; GPU selected, capture/inference metrics update, source changes disabled during a session |
| Existing test-video source and detection overlay | Passed; video, boxes and labels visible together in portrait/landscape |
| Navigation | Passed; toolbar and system back return from Models; locale recreation preserves Models and its result |
| Model import | Passed through system file picker with the bundled signed package; reimport of the existing version succeeds |
| Activate and roll back | Passed using the two installed versions; initial active/previous pointers restored |
| Models during a session | Import, activation and rollback disabled with an explanation |
| English/French | Screen text, source menu and outcomes update; changing locale closes an open source menu |
| Telemetry | Upload log observed, including batch count and compressed/raw sizes; worker factory initializes successfully |
| Portrait/landscape | Passed after the HUD fix below; Stop remains stopped after rotation |

The initial model pointers were `efficientdet-lite0-int8` (active) and `efficientdet-lite0-b` (previous).
The locale override was empty (follow system), and rotation was automatic; both preferences were restored.
Temporary import and instrumentation artifacts were removed. The app remains installed with its existing models.

## Layout correction

A single-column HUD exceeded the available landscape height: the thermal row was clipped and memory was invisible.
`HudLayoutTest.everyMetricRemainsVisibleInLandscape` reproduced the missing memory row before the fix and passes
after it. The landscape HUD now uses two columns; portrait retains the original arrangement. Runtime screenshot and
accessibility bounds confirm that every row fits. The import action now reads “Import” / “Importer”, avoiding a
clipped second line in the fixed-width bottom action buttons.

## Automated checks

- `./gradlew spotlessApply && ./gradlew check :app:assembleDebug`: passed; 712 JVM tests, no failures or skips.
- `:features:telemetry:room:connectedDebugAndroidTest`: 10 passed.
- `:features:live:ui:connectedDebugAndroidTest`: 1 passed (landscape regression).
- Initial `:features:live:inference-litert:connectedDebugAndroidTest`: 1 failed because `golden.txt` had not been approved.
  Model loading and repeated inference completed before that assertion. The candidate output was collected separately
  under `/tmp/framelock-device/golden-candidate.txt`; it was not added to test assets or treated as an approved oracle.

Tests run in library test packages so the installed application's data is preserved.

## LiteRT reference correction

The synthetic gradient produced only low-confidence noise. Its candidate was rejected as a reference and remains
outside the repository. The test now uses a real frame from the existing CC BY 4.0 video, with two people annotated
before inspecting inference output. [Fixture provenance](../features/live/inference-litert/src/androidTest/assets/classroom-004s.md)
records the source, transformations, hashes and pixel coordinates.

Each reference requires a distinct `person` detection with score ≥ 0.5 and intersection-over-union ≥ 0.8. Order,
extra detections and exact scores do not matter. Separate tests force CPU and GPU, assert the selected backend,
and exercise both RGBA and YUV preprocessing. There is no fallback or generated reference to approve.

| Backend / input | Foreground person: score / IoU | Standing person: score / IoU |
|---|---|---|
| CPU / RGBA | 0.688 / 0.872 | 0.840 / 0.908 |
| CPU / YUV | 0.707 / 0.872 | 0.840 / 0.920 |
| GPU / RGBA | 0.738 / 0.857 | 0.840 / 0.905 |
| GPU / YUV | 0.738 / 0.871 | 0.840 / 0.907 |

The replacement suite passed all 10 instrumented tests: four inference combinations and six matching regressions
(order/ties/extras, missing/wrong-class/low-confidence detections, duplicate assignment, overlapping candidates,
IoU boundaries and empty references). Together with the 11 earlier telemetry and HUD checks, the device checks
now have 21 passing tests and no unresolved reference-data failure. The raw results and logs are under
`/tmp/framelock-litert/instrumented/`; measured matches are in `/tmp/framelock-litert/detection-evidence.json`.

The exact colour-conversion JVM tests remain necessary: a detector may still recognize a person after a colour
error. These device assertions cover object-level results, not bit-exact preprocessing or other phone GPUs.

## Remaining checks

Fresh-install bootstrap was not exercised because the existing app data was retained. API 29–30 fallback rendering,
a physical UVC capture card, and a rendered Android Studio preview comparison remain untested in this run. This is a
functional device check, not evidence of allocation rate, sustained performance or frame-to-frame synchronization.
