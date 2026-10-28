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
- `:features:live:inference-litert:connectedDebugAndroidTest`: 1 failed because `golden.txt` has not been approved.
  Model loading and repeated inference completed before that assertion. The candidate output was collected separately
  under `/tmp/framelock-device/golden-candidate.txt`; it was not added to test assets or treated as an approved oracle.

The instrumented suite therefore has 11 passing tests and one unresolved reference-data failure. Tests run in
library test packages so the installed application's data is preserved.

## Remaining checks

Fresh-install bootstrap was not exercised because the existing app data was retained. API 29–30 fallback rendering,
a physical UVC capture card, and a rendered Android Studio preview comparison remain untested in this run. This is a
functional device check, not evidence of allocation rate, sustained performance or frame-to-frame synchronization.
