# Run public-cpu

2026-10-26T16:14:06Z on test phone (Android 16, SDK 36), app 0.1.0, commit 37ee0cc-dirty.

- Change: public trace, cpu (illustration, not a measurement)
- Backend: CPU (requested cpu)
- Duration: 60 s; steady state = 4 of 6 windows (first 30 s ignored)
- Input video SHA-256: `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`
- Start: thermal status 0, battery 33.4 °C. End: thermal status 1, battery 33.7 °C.

| Figure | Value |
|---|---|
| Inference p50 / p95 (ms) | 47.2 / 48.3 |
| End-to-end p50 / p95 (ms) | 95.6 / 115.2 |
| Input fps | 30.0 |
| Inference fps | 19.6 |
| Dropped frames (steady windows) | 446 |
| CPU % of one core, mean / max | 256 / 257 |
| PSS (MB) | 275 |
| &nbsp;&nbsp;Java Heap (MB) | 32.7 |
| &nbsp;&nbsp;Native Heap (MB) | 71.4 |
| &nbsp;&nbsp;Code (MB) | 36.3 |
| &nbsp;&nbsp;Stack (MB) | 2.7 |
| &nbsp;&nbsp;Graphics (MB) | 106.4 |
| &nbsp;&nbsp;Private Other (MB) | 9.6 |
| &nbsp;&nbsp;System (MB) | 15.8 |
| CPU sensor start → end (°C) | 37.4 → 45.6 |
| battery sensor start → end (°C) | 0.0 → 0.0 |
| skin sensor start → end (°C) | 35.9 → 39.0 |

## Notes

- the trace has no section slices of dev.framelock (was the app traced? atrace_apps)

Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).
