# Performance results

Method: [README.md](README.md) and "Performance method" in [../architecture.md](../architecture.md). One change at a
time, each one re-measured on the same device, with the same input video and the same start conditions, then logged
here. The numbers come from `scripts/perf/run.sh`; each row links to its committed summary
(`runs/<label>/summary.md`). The raw logs, samples and Perfetto traces stay in `runs/<label>/` on the machine that ran
the measurement: they are large, and they name the device by serial number and model.

- **Input**: `classroom.mp4`, 1920x1080, H.264, 30 fps, 32.8 s, played in a loop through `capture-file`.
  SHA-256 `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`. Source and licence: see "Input video".
- **Run**: 10 minutes after a cool-down gate (thermal status 0, battery at or below 33 °C), debug build, HUD on.
- **Figures**: median over the 10 s windows after the first 30 s. Inference and end-to-end latency are the p50 / p95 of
  each window (`PipelineWindow`), then the median of the windows. Drops are summed over those windows.
  CPU % is the mean of the app process's CPU time over wall time, in percent of one core (a value above 100 uses
  several cores). PSS is `dumpsys meminfo` TOTAL PSS at the end. Temperatures are the battery temperature (and the
  skin sensor where the device reports it) at the start and after the run.
- **Backend**: what the engine reported at load (`LiteRtEngine` in Logcat), not what was asked.

| Run | Date | Device | Backend | Change | Inference p50 / p95 (ms) | E2E p50 / p95 (ms) | Input fps | Inference fps | Drops | CPU % | PSS (MB) | Temp. start → end (°C) | Trace |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| [baseline](runs/baseline/summary.md) | 2026-10-24 | test phone, Android 16 | GPU | debug build, GPU auto, file source 1080p30, USB powered | 55.9 / 76.3 | 1130.1 / 1408.0 | 4.2 | 2.6 | 892 | 185 | 400 | 28.7 → 31.8 (skin 33.0 → 36.9) | `runs/baseline/baseline.perfetto-trace` (local) |
| [opt1-render](runs/opt1-render/summary.md) | 2026-10-24 | test phone, Android 16 | GPU | render at on-screen size, table-driven loops | 56.1 / 66.5 | 264.5 / 302.7 | 11.1 | 9.3 | 1207 | 201 | 354 | 32.5 → 32.6 (skin 34.9 → 38.7) | `runs/opt1-render/opt1-render.perfetto-trace` (local) |
| [opt2-preprocess](runs/opt2-preprocess/summary.md) | 2026-10-24 | test phone, Android 16 | GPU | preprocess with cached tables and bulk reads (on top of opt1) | 59.9 / 73.2 | 168.8 / 190.6 | 11.4 | 11.4 | 57 | 201 | 355 | 32.9 → 34.0 (skin 35.9 → 39.1) | `runs/opt2-preprocess/opt2-preprocess.perfetto-trace` (local) |
| [opt3-capture](runs/opt3-capture/summary.md) | 2026-10-24 | test phone, Android 16 | GPU | allocation-free plane copy at capture (on top of opt2) | 63.1 / 71.0 | 119.2 / 141.3 | 30.0 | 14.3 | 9241 | 188 | 368 | 33.0 → 32.4 (skin 34.7 → 37.9) | `runs/opt3-capture/opt3-capture.perfetto-trace` (local) |

## Findings (M7)

Per-section durations from the Perfetto traces (p50 / p95 in ms, steady state):

| Section | Thread | baseline | opt1-render | opt2-preprocess | opt3-capture |
|---|---|---|---|---|---|
| CAPTURE (copy into the pool) | decode | skipped mostly (pool starved) | 77.7 / 96.9 | 75.7 / 86.0 | **5.6 / 14.8** |
| PREPROCESS (frame → 320x320 RGB) | inference | 38.6 / 64.4 | 47.3 / 66.6 | **2.2 / 10.6** | 2.0 / 5.2 |
| INFERENCE (LiteRT, GPU) | inference | 93.9 / 138.3 | 104.4 / 130.5 | 63.1 / 78.9 | 65.5 / 85.3 |
| POSTPROCESS | inference | 0.2 / 0.5 | 0.2 / 0.3 | 0.2 / 0.2 | 0.2 / 0.3 |
| RENDER (frame → bitmap) | main | 360.5 / 366.4 | **9.0 / 9.5** | 9.1 / 12.2 | 9.8 / 12.9 |

1. **Render (opt1).** The frame was converted at full resolution, one byte read and one division per sample, on the
   main thread inside `DisplaySlot.read` (ADR-010). The 360 ms held the displayed buffer that long, so the source pool
   ran dry and skipped frames: 4 input fps and 1.1 s end to end. Converting only the on-screen rectangle with bulk row
   reads and cached sampling tables brought it to 9 ms; ADR-010 is unchanged (same thread, same slot contract).
2. **Preprocess (opt2).** Same technique on the inference thread: 39–47 ms → 2 ms, with a model input checked
   bit-identical to the previous algorithm. Inference fps then matched input fps.
3. **Capture (opt3).** The I420 pools allocated a buffer view per row (about 1 600 per 1080p frame, a breach of ADR-012)
   and copied interleaved chroma one byte at a time: 76 ms → 6 ms. The source then delivers the video's full 30 fps.
4. **What is left.** Inference itself (about 65 ms on the GPU delegate) now bounds the rate at about 14 analyses per
   second. The large drop count of opt3 is the intended latest-frame-wins behaviour (ADR-002) with a 30 fps input,
   not a loss. End to end: 1.13 s → 119 ms (p50), 9.5x; analyses per second: 2.6 → 14.3, 5.5x; PSS and CPU about flat.
5. **Not measured yet.** GPU versus CPU backend (`--backend cpu`), a lower input resolution, an inference rate cap,
   the cost of the glass blur, the USB capture path (RGBA, extra surface copy) and the camera path (CameraX rotation,
   about 15 input fps in M3). Allocation was not profiled (PIPE-6b): opt3 removed the one breach found by reading code.

Conditions: same phone for every run, powered over USB, cool-down gate passed each time; the CPU sensor reached about
42–44 °C after 10 minutes in every run, without thermal degradation (status 0 or 1).

## Input video

`classroom.mp4` (CC BY 4.0, unmodified; source and attribution in `docs/soup.md`). Not stored in this repository:
`scripts/perf/fetch-input.sh` downloads it and checks the SHA-256.
