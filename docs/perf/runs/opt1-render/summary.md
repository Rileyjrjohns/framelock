# Run opt1-render

2026-10-24T09:26:23Z on test phone (Android 16, SDK 36), app 0.1.0, commit d76b547-dirty.

- Change: render at on-screen size, table-driven loops
- Backend: GPU (requested auto)
- Duration: 600 s; steady state = 57 of 59 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 32.5 °C. End: thermal status 1, battery 32.6 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 56.1 / 66.5 |
| End-to-end p50 / p95 (ms) | 264.5 / 302.7 |
| Input fps | 11.1 |
| Inference fps | 9.3 |
| Dropped frames (steady windows) | 1207 |
| CPU % of one core, mean / max | 201 / 241 |
| PSS (MB) | 354 |
| &nbsp;&nbsp;Java Heap (MB) | 43.5 |
| &nbsp;&nbsp;Native Heap (MB) | 86.8 |
| &nbsp;&nbsp;Code (MB) | 67.5 |
| &nbsp;&nbsp;Stack (MB) | 2.6 |
| &nbsp;&nbsp;Graphics (MB) | 122.1 |
| &nbsp;&nbsp;Private Other (MB) | 10.4 |
| &nbsp;&nbsp;System (MB) | 20.9 |
| CPU sensor start → end (°C) | 36.8 → 43.0 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 34.9 → 38.7 |

## Trace sections (from the Perfetto trace, steady state)

| Section | Count | p50 (ms) | p95 (ms) |
|---|---|---|---|
| CAPTURE | 6438 | 77.73 | 96.87 |
| PREPROCESS | 5262 | 47.27 | 66.58 |
| INFERENCE | 5261 | 104.42 | 130.53 |
| POSTPROCESS | 5262 | 0.18 | 0.27 |
| RENDER | 5262 | 9.00 | 9.51 |

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
