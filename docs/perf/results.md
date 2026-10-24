# Performance results

Method: [README.md](README.md) and "Performance method" in [../architecture.md](../architecture.md). One change at a
time, each one re-measured on the same device, with the same input video and the same start conditions, then logged
here. The numbers come from `scripts/perf/run.sh`; each row links to its run directory
(`runs/<label>/summary.md`, the raw logs and the samples). Perfetto traces are large and not committed: they stay in
`runs/<label>/` on the machine that ran the measurement.

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

## Input video

`classroom.mp4` (CC BY 4.0, unmodified; source and attribution in `docs/soup.md`). Not stored in this repository:
`scripts/perf/fetch-input.sh` downloads it
and checks the SHA-256.
