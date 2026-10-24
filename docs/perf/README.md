# Performance method

Every performance claim is backed by a measured run in this directory.

1. **Baseline**: a 10-minute run on a fixed input video played through the capture chain, with a Perfetto system trace
   (CPU frequencies, scheduling, GPU, thermal), the app's own latency windows, CPU %, PSS and temperatures.
2. **One change at a time**, re-measured the same way, logged in [results.md](results.md): inference p50/p95,
   end-to-end p50/p95, CPU %, PSS, temperature after 10 minutes.
3. **Candidates**: GPU delegate vs CPU, input resolution, zero-copy YUV → tensor, MJPEG decoding cost from the capture
   card, inference rate cap, buffer pooling, LiteRT `CompiledModel` (2.x).

This page is how to take a measurement. It covers the tooling only (M7, step 1): no optimisation is part of it.

## What is measured, and how

- **Input**: `classroom.mp4` (1920x1080, H.264, 30 fps, 32.8 s, CC BY 4.0, see [soup.md](../soup.md)), decoded by
  `:features:live:capture-file` and delivered at the file's own rate, in a loop. It enters the pipeline like a camera
  frame (I420 buffers from a pool of four, arrival timestamps), so latest-frame-wins, drops and the display path are
  the real ones. What it does not exercise: camera or capture-card capture cost (UVC decoding, CameraX rotation).
- **Numbers**: the app logs one `PipelineWindow` per 10 s (tag `Telemetry`, debug builds). `summarize.py` ignores the
  first 30 s, then takes the median of the windows for each figure. CPU % comes from `/proc/<pid>/stat` every 10 s,
  PSS from `dumpsys meminfo`, temperatures from `dumpsys battery` and `dumpsys thermalservice`.
- **Trace sections**: `CAPTURE` (decoder copy), `PREPROCESS`, `INFERENCE`, `POSTPROCESS` (engine, pipeline) and `RENDER`
  (conversion and bitmap upload on the main thread) are `androidx.tracing` sections, visible in Perfetto under the app's
  threads. If `trace_processor_shell` is on your PATH, `summarize.py` also reads their p50/p95 from the trace.
- **Backend**: the HUD shows `Backend GPU` or `Backend CPU`, and the engine logs it once per load (`adb logcat -s
  LiteRtEngine`). The run script can force one with `--backend gpu|cpu` (debug builds only).

## Run a baseline, step by step

Prerequisites: JDK 17, `adb` on the PATH, one device with USB or wireless debugging, Python 3, and the signed debug
model (`./scripts/fetch-model.sh`, once).

```bash
./scripts/fetch-model.sh                 # once: model + development key for the debug build
./gradlew installDebug                   # the debug build of the code under test
./scripts/perf/fetch-input.sh            # once per device: downloads the video (SHA-256 checked) and pushes it
./scripts/perf/run.sh baseline-gpu 600   # waits for the device to cool down, then runs 10 minutes
```

`run.sh <label> [seconds]` (see `--help`): `--max-start-temp 33` (battery °C to start under), `--backend auto|gpu|cpu`,
`--note "what changed"`, `--no-trace`, `--force`. With several devices attached set `ANDROID_SERIAL`. A run writes
`docs/perf/runs/<label>/` (summary, raw logs, samples; the trace is gitignored) and adds a row to
[results.md](results.md). Commit `summary.md`, the text artefacts and the row; the `.perfetto-trace` stays local
(typically hundreds of MB for ten minutes; `--no-trace` skips it).

For a comparison, change one thing in the code, `./gradlew installDebug`, and run again with a new label
(`baseline-gpu`, then `xnnpack-cpu`, `no-extra-copy`, and so on). Compare rows of the same device.

### Conditions to hold constant

- The same device, the same OS build, the same battery level range, charging state and screen brightness. Charging heats
  the phone: prefer the same cable state for every run, or wireless debugging with the phone unplugged.
- Airplane mode and no other app in the foreground; the debug launch keeps the screen on by itself.
- Start cold: the script waits for thermal status 0 and a battery temperature at or below `--max-start-temp`, and
  records both. A run that starts warmer is not comparable with one that started cool: the gate refuses it.
- Debug builds are not release builds (no R8, debuggable). Numbers are comparable between debug runs only. A release
  re-measurement belongs to the end of M7.
- Do not touch the phone during a run.

### Read the results

- `docs/perf/runs/<label>/summary.md`: the figures, the sections from the trace when available, notes (an early end, a
  backend that differs from the one requested, a missing log).
- `docs/perf/results.md`: one row per run.
- The trace: open `docs/perf/runs/<label>/<label>.perfetto-trace` in <https://ui.perfetto.dev> ("Open trace file"; the
  trace stays on your machine). Look at the app's process: the `CAPTURE`, `PREPROCESS`, `INFERENCE`, `POSTPROCESS` and
  `RENDER` slices per thread (`file-decode`, `inference`, main), CPU frequency and idle tracks, scheduling, and the
  thermal and power counters where the device reports them.
- `trace_processor_shell` (from the Perfetto project, not downloaded by these scripts) enables the per-section p50/p95
  in the summary: put it on your PATH, then `python3 scripts/perf/summarize.py docs/perf/runs/<label>` re-reads a run.

## Putting the video on the device by hand

The app reads `filesDir/perf/input.mp4`, in its private storage:

```bash
adb push input.mp4 /data/local/tmp/input.mp4
adb shell run-as dev.framelock sh -c 'mkdir -p files/perf && cp /data/local/tmp/input.mp4 files/perf/input.mp4'
```

`run-as` works on debuggable builds only. If the copy is refused on a device, `fetch-input.sh` streams the file through
`adb shell -T run-as dev.framelock sh -c 'cat > files/perf/input.mp4' < input.mp4` instead. To use another H.264 MP4, run
`fetch-input.sh --file my.mp4`: the SHA-256 recorded by each run is the one of the file on the device. The "Test video"
option then appears in the source picker (debug builds, when the file exists).

## Driving the app by hand

A debug build honours intent extras, so a run needs no taps (release builds ignore them):

```bash
adb shell am force-stop dev.framelock
adb shell am start -n dev.framelock/.MainActivity -e source file --ez autostart true --ez hud true -e backend cpu
```

`source` is `auto`, `back`, `front`, `external`, `file` or a UVC device id (`uvc:<id>`); `autostart` starts the pipeline
once a model is active (the first debug launch imports the bundled one); `hud` shows the HUD; `backend` is `auto`
(default, the production behaviour), `gpu` or `cpu`. A forced `gpu` that cannot be built fails the model load with a
message in the status line instead of falling back. The camera permission is requested by the live screen even for the
test video; `run.sh` grants it first (`pm grant`).

## Known limits

- The loop restarts by flushing the decoder and seeking to the first sample: the first frames of each pass can arrive a
  little late. The schedule continues across the loop, so the input rate stays the file's.
- A 10-minute system trace is large; Perfetto writes it to a file on the device while the run goes on, and the script
  retries with a smaller config if the device's Perfetto does not know a newer field.
- CPU % is the app process only (not the system); a value above 100 means more than one core.
- End-to-end latency is render time minus capture time (arrival at the source here); it excludes compositor and display.
