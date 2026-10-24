# Run baseline

2026-10-24T09:04:16Z on test phone (Android 16, SDK 36), app 0.1.0, commit 625e830-dirty.

- Change: debug build, GPU auto, file source 1080p30, USB powered
- Backend: GPU (requested auto)
- Duration: 600 s; steady state = 57 of 59 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 28.7 °C. End: thermal status 1, battery 31.8 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 55.9 / 76.3 |
| End-to-end p50 / p95 (ms) | 1130.1 / 1408.0 |
| Input fps | 4.2 |
| Inference fps | 2.6 |
| Dropped frames (steady windows) | 892 |
| CPU % of one core, mean / max | 185 / 195 |
| PSS (MB) | 400 |
| &nbsp;&nbsp;Java Heap (MB) | 50.6 |
| &nbsp;&nbsp;Native Heap (MB) | 101.8 |
| &nbsp;&nbsp;Code (MB) | 65.2 |
| &nbsp;&nbsp;Stack (MB) | 2.8 |
| &nbsp;&nbsp;Graphics (MB) | 153.0 |
| &nbsp;&nbsp;Private Other (MB) | 10.6 |
| &nbsp;&nbsp;System (MB) | 16.0 |
| CPU sensor start → end (°C) | 34.4 → 40.8 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 33.0 → 36.9 |

## Trace sections (from the Perfetto trace, steady state)

| Section | Count | p50 (ms) | p95 (ms) |
|---|---|---|---|
| CAPTURE | 17006 | 0.04 | 99.18 |
| PREPROCESS | 1659 | 38.55 | 64.42 |
| INFERENCE | 1659 | 93.87 | 138.28 |
| POSTPROCESS | 1659 | 0.21 | 0.49 |
| RENDER | 1658 | 360.48 | 366.37 |

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
