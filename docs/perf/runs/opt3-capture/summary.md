# Run opt3-capture

2026-10-24T10:03:53Z on test phone (Android 16, SDK 36), app 0.1.0, commit 2751cb1.

- Change: allocation-free plane copy at capture (on top of opt2)
- Backend: GPU (requested auto)
- Duration: 600 s; steady state = 58 of 60 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 33.0 °C. End: thermal status 1, battery 32.4 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 63.1 / 71.0 |
| End-to-end p50 / p95 (ms) | 119.2 / 141.3 |
| Input fps | 30.0 |
| Inference fps | 14.3 |
| Dropped frames (steady windows) | 9241 |
| CPU % of one core, mean / max | 188 / 209 |
| PSS (MB) | 368 |
| &nbsp;&nbsp;Java Heap (MB) | 41.3 |
| &nbsp;&nbsp;Native Heap (MB) | 64.1 |
| &nbsp;&nbsp;Code (MB) | 65.7 |
| &nbsp;&nbsp;Stack (MB) | 1.5 |
| &nbsp;&nbsp;Graphics (MB) | 135.4 |
| &nbsp;&nbsp;Private Other (MB) | 10.2 |
| &nbsp;&nbsp;System (MB) | 50.4 |
| CPU sensor start → end (°C) | 36.7 → 42.3 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 34.7 → 37.9 |

## Trace sections (from the Perfetto trace, steady state)

| Section | Count | p50 (ms) | p95 (ms) |
|---|---|---|---|
| CAPTURE | 17108 | 5.59 | 14.83 |
| PREPROCESS | 7996 | 2.04 | 5.23 |
| INFERENCE | 7995 | 65.51 | 85.31 |
| POSTPROCESS | 7996 | 0.17 | 0.32 |
| RENDER | 7997 | 9.83 | 12.86 |

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
