# Run public-gpu

2026-10-26T16:11:43Z on test phone (Android 16, SDK 36), app 0.1.0, commit 37ee0cc-dirty.

- Change: public trace, gpu (illustration, not a measurement)
- Backend: GPU (requested gpu)
- Duration: 60 s; steady state = 3 of 5 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 33.3 °C. End: thermal status 1, battery 33.5 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 54.9 / 62.9 |
| End-to-end p50 / p95 (ms) | 106.2 / 124.5 |
| Input fps | 30.0 |
| Inference fps | 16.9 |
| Dropped frames (steady windows) | 396 |
| CPU % of one core, mean / max | 187 / 188 |
| PSS (MB) | 327 |
| &nbsp;&nbsp;Java Heap (MB) | 35.5 |
| &nbsp;&nbsp;Native Heap (MB) | 85.8 |
| &nbsp;&nbsp;Code (MB) | 46.1 |
| &nbsp;&nbsp;Stack (MB) | 2.7 |
| &nbsp;&nbsp;Graphics (MB) | 131.2 |
| &nbsp;&nbsp;Private Other (MB) | 9.6 |
| &nbsp;&nbsp;System (MB) | 15.7 |
| CPU sensor start → end (°C) | 39.7 → 45.5 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 36.8 → 38.7 |

## Notes

- the trace has no section slices of dev.framelock (was the app traced? atrace_apps)

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
