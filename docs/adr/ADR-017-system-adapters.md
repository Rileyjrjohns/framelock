# ADR-017: System adapters (M4): thermal headroom and memory pressure

- Status: Accepted
- Date: 2026-10-23

## Context

ADR-013 maps `(ThermalLevel, MemoryPressure)` to a rate, but M3 only fed it the thermal status listener and a
constant `NORMAL` memory pressure. The status listener reports a throttle after it happened; and on recent Android
versions the memory callbacks are mostly silent. This ADR records what each platform signal really delivers (read in
the references below, 2026-10-23) and how the adapters use it. Thresholds are a starting point, tuned with
measurements in M7.

## Decision

### Thermal: the worse of the listener and the forecast headroom

`PowerManagerThermalMonitor` emits the worse of:

1. the status from `addThermalStatusListener` (API 29+), mapped by `ThermalLevels` as before;
2. on API 30+, a level derived from `getThermalHeadroom(10)`, so the pipeline slows down before the platform throttles.

What the platform documents for `getThermalHeadroom(int forecastSeconds)` (added in API 30,
[PowerManager](https://developer.android.com/reference/android/os/PowerManager); the rate limit is visible in
[PowerManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/PowerManager.java)):

- 1.0 is the `THERMAL_STATUS_SEVERE` throttling threshold. Values in (0, 1] scale linearly with temperature; values
  above 1.0 have no implied mapping to a status. Negative values are clamped to 0.
- "No benefit to calling this function more frequently than about once per second"; calls significantly faster return
  NaN. The platform code rejects calls less than 500 ms after the previous successful one.
- NaN also means the device does not support it. Forecasting needs several samples: for the first seconds after the
  first call the current headroom is returned whatever `forecastSeconds` is. `forecastSeconds` is 0..60.
- API 35 adds `getThermalHeadroomThresholds()` (per-status headroom, only for statuses the manufacturer defines) and
  API 36 a change listener. Not used: the map may lack the statuses we need, and the fixed scale above is enough to
  act before SEVERE, the only status the headroom is anchored to.

Choices:

- Poll every **2 s** (twice the advised minimum), forecast **10 s** ahead, only while the flow is collected. NaN, an
  unsupported device or a failing call leaves the headroom level unchanged (NONE if it never answered).
- `HeadroomLevels` (pure, JVM-tested): below 0.80 NONE; 0.80 to 0.90 LIGHT (no cap in ADR-013); 0.90 to 1.00 MODERATE
  (15 fps); 1.00 and above SEVERE (10 fps). The forecast never asks for more than SEVERE, because the platform gives no
  meaning above 1.0; CRITICAL and worse come from the listener only.
- A level is left only when the forecast is 0.05 below the threshold that entered it. Without it, the cap itself
  lowers the temperature and the rate would flip on every poll.
- `SharedThermalMonitor` and `SharedMemoryPressureMonitor` (`stateIn`, `WhileSubscribed(5 s)`) give the pipeline and the
  debug HUD one subscription, so there is a single poller per signal.

### Memory: the worse of trim callbacks and `ActivityManager.MemoryInfo`

What the platform delivers
([ComponentCallbacks2](https://developer.android.com/reference/android/content/ComponentCallbacks2),
[ComponentCallbacks](https://developer.android.com/reference/android/content/ComponentCallbacks)):

| Level (value) | Up to API 33 | API 34 and later |
|---|---|---|
| `TRIM_MEMORY_RUNNING_MODERATE` (5), `_RUNNING_LOW` (10), `_RUNNING_CRITICAL` (15) | delivered | "Apps are not notified of this level since API level 34" (deprecated in 35) |
| `TRIM_MEMORY_UI_HIDDEN` (20), `_BACKGROUND` (40) | delivered | delivered (not deprecated) |
| `TRIM_MEMORY_MODERATE` (60), `_COMPLETE` (80) | delivered | not delivered (deprecated in 35) |
| `onLowMemory()` | delivered | "Since API level 34 this is never called" (deprecated in 35) |

So from API 34 the only trim callbacks left tell a hidden or background process where it stands in the LRU list; none
tells a foreground app that the device is low on memory. Polling is therefore the primary signal on API 34+, and
the callbacks are kept for API 29 to 33.

`AndroidMemoryPressureMonitor`:

- `ComponentCallbacks2` registered on the application context while collected. `TrimLevels`: RUNNING_MODERATE and
  RUNNING_LOW give MODERATE, RUNNING_CRITICAL and `onLowMemory` give CRITICAL. UI_HIDDEN and the background levels give
  no signal: they describe the process, not memory available to a running pipeline.
- Decay: a callback is an event with no "all clear". A signal is held **30 s** (`TrimHold`), a lower one never cuts
  a higher one short, then it lapses to NORMAL.
- `getMemoryInfo`, polled every **2 s** into a reused `MemoryInfo`. `MemoryInfoPressure`: available memory at or below
  `threshold` (or `lowMemory`) gives CRITICAL (pause, ADR-013); up to twice the threshold, MODERATE (15 fps); above,
  NORMAL. `threshold` is the level where the system starts killing background processes, so the pipeline backs off
  while there is still room. A level is left only 10 % above its bound.
- The Java heap (`Runtime` used against `maxMemory`) is not observed: used heap includes garbage not yet collected, so
  the ratio is noisy and would pause a healthy pipeline; frames use pooled buffers and the model native memory, not
  the Java heap, which is the part those numbers would not describe.

### Device snapshot

Unchanged: `DeviceSnapshotRecorded(model, osVersion, batteryPct, batteryTempC, thermalLevel)` once per start, as
architecture.md lists. `thermalLevel` is the platform status, not the forecast. The strings are bounded tokens
(ADR-004).

### Debug HUD

`LiveViewModel` receives the shared thermal and memory `StateFlow`s and the HUD shows the current levels on one line.
They change rarely and nothing is built per frame.

## Not observed

- Thermal: the headroom thresholds and change listener (API 35/36), battery temperature as a signal (it is only in the
  device snapshot), CPU or GPU frequencies.
- Memory: the Java heap, native heap, PSS, per-process `lastTrimLevel` (`ActivityManager.getMyMemoryState`), and the
  memory used by the capture card driver.
- Checked on Android 16 (2026-10-23): the shell still injects `RUNNING_CRITICAL`, which paused the pipeline for the
  30 s hold, then it resumed. Unverified on a device: the real
  `threshold` values on the test devices, and how soon `getThermalHeadroom` answers after start.

## Consequences

The pipeline can degrade 1 to 2 poll intervals (2 to 4 s) late on memory and a few seconds early on heat. Both
thresholds are untested against real devices until the checks in `docs/traceability.md` (SYS-5 to SYS-7) are run.
