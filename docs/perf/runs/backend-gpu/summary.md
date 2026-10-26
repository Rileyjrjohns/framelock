# Run backend-gpu

2026-10-26T16:28:14Z on test phone (Android 16, SDK 36), app 0.1.0, commit 37ee0cc-dirty.

- Change: GPU delegate forced (after opt3)
- Backend: GPU (requested gpu)
- Duration: 600 s; steady state = 57 of 59 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 33.8 °C. End: thermal status 1, battery 34.4 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 62.9 / 70.4 |
| End-to-end p50 / p95 (ms) | 119.1 / 140.4 |
| Input fps | 30.0 |
| Inference fps | 14.4 |
| Dropped frames (steady windows) | 8949 |
| CPU % of one core, mean / max | 190 / 215 |
| PSS (MB) | 327 |
| &nbsp;&nbsp;Java Heap (MB) | 32.8 |
| &nbsp;&nbsp;Native Heap (MB) | 87.6 |
| &nbsp;&nbsp;Code (MB) | 46.0 |
| &nbsp;&nbsp;Stack (MB) | 2.8 |
| &nbsp;&nbsp;Graphics (MB) | 130.1 |
| &nbsp;&nbsp;Private Other (MB) | 9.5 |
| &nbsp;&nbsp;System (MB) | 18.6 |
| CPU sensor start → end (°C) | 35.4 → 43.5 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 34.5 → 38.8 |

## Trace sections (from the Perfetto trace, steady state)

| Section | Count | p50 (ms) | p95 (ms) |
|---|---|---|---|
| CAPTURE | 17058 | 5.69 | 14.36 |
| PREPROCESS | 8111 | 2.04 | 4.85 |
| INFERENCE | 8110 | 65.48 | 79.03 |
| POSTPROCESS | 8111 | 0.17 | 0.36 |
| RENDER | 8111 | 9.90 | 12.56 |

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
