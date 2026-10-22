# Performance method

Every performance claim is backed by a trace in this directory.

1. **Baseline**: Perfetto system trace (CPU frequencies, GPU, thermal), memory profiler, 10-minute run on a fixed
   input video played through the capture chain.
2. **One change at a time**, re-traced, logged in [results.md](results.md): inference p50/p95, end-to-end p50/p95,
   CPU %, PSS, temperature after 10 minutes.
3. **Candidates**: GPU delegate vs CPU, input resolution, zero-copy YUV → tensor, MJPEG decoding cost from the capture
   card, inference rate cap, buffer pooling, LiteRT `CompiledModel` (2.x).

Traces go in `traces/`, named `<date>-<device>-<change>.perfetto-trace`.
