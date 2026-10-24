# Run opt2-preprocess

2026-10-24T09:42:07Z on test phone (Android 16, SDK 36), app 0.1.0, commit a7e186e-dirty.

- Change: preprocess with cached tables and bulk reads (on top of opt1)
- Backend: GPU (requested auto)
- Duration: 600 s; steady state = 57 of 59 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 32.9 °C. End: thermal status 1, battery 34.0 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 59.9 / 73.2 |
| End-to-end p50 / p95 (ms) | 168.8 / 190.6 |
| Input fps | 11.4 |
| Inference fps | 11.4 |
| Dropped frames (steady windows) | 57 |
| CPU % of one core, mean / max | 201 / 242 |
| PSS (MB) | 355 |
| &nbsp;&nbsp;Java Heap (MB) | 34.7 |
| &nbsp;&nbsp;Native Heap (MB) | 68.5 |
| &nbsp;&nbsp;Code (MB) | 66.3 |
| &nbsp;&nbsp;Stack (MB) | 1.7 |
| &nbsp;&nbsp;Graphics (MB) | 132.1 |
| &nbsp;&nbsp;Private Other (MB) | 10.2 |
| &nbsp;&nbsp;System (MB) | 41.6 |
| CPU sensor start → end (°C) | 38.3 → 43.7 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 35.9 → 39.1 |

## Trace sections (from the Perfetto trace, steady state)

| Section | Count | p50 (ms) | p95 (ms) |
|---|---|---|---|
| CAPTURE | 6636 | 75.70 | 86.04 |
| PREPROCESS | 6592 | 2.16 | 10.61 |
| INFERENCE | 6591 | 63.05 | 78.93 |
| POSTPROCESS | 6592 | 0.17 | 0.22 |
| RENDER | 6593 | 9.07 | 12.21 |

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
