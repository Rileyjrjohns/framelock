# Run backend-cpu

2026-10-26T16:07:46Z on test phone (Android 16, SDK 36), app 0.1.0, commit 37ee0cc-dirty.

- Change: CPU (XNNPACK) forced (after opt3); started at a warmer battery than the GPU run
- Backend: CPU (requested cpu)
- Duration: 600 s; steady state = 57 of 59 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 30.4 °C. End: thermal status 1, battery 33.0 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 69.0 / 87.6 |
| End-to-end p50 / p95 (ms) | 127.7 / 155.8 |
| Input fps | 30.0 |
| Inference fps | 13.0 |
| Dropped frames (steady windows) | 8772 |
| CPU % of one core, mean / max | 249 / 260 |
| PSS (MB) | 262 |
| &nbsp;&nbsp;Java Heap (MB) | 23.5 |
| &nbsp;&nbsp;Native Heap (MB) | 63.4 |
| &nbsp;&nbsp;Code (MB) | 33.7 |
| &nbsp;&nbsp;Stack (MB) | 1.6 |
| &nbsp;&nbsp;Graphics (MB) | 97.0 |
| &nbsp;&nbsp;Private Other (MB) | 9.0 |
| &nbsp;&nbsp;System (MB) | 34.1 |
| CPU sensor start → end (°C) | 32.7 → 43.2 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 31.1 → 38.5 |

## Trace sections (from the Perfetto trace, steady state)

| Section | Count | p50 (ms) | p95 (ms) |
|---|---|---|---|
| CAPTURE | 12335 | 8.59 | 20.75 |
| PREPROCESS | 6190 | 1.83 | 5.31 |
| INFERENCE | 6189 | 60.39 | 94.93 |
| POSTPROCESS | 6190 | 0.15 | 0.26 |
| RENDER | 6190 | 8.49 | 12.74 |

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
