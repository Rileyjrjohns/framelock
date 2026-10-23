# ADR-015: First real loop (M2) choices

- Status: Accepted
- Date: 2026-10-23

## Context

M2 connects a real source, a real runtime and a screen. Several choices are temporary or need measuring later.

## Decision

- **Camera frames are copied into a pool.** CameraX recycles few images, while the pipeline may hold three (pending,
  inferring, displayed; ADR-010). The CameraX adapter copies each image into a fixed pool of four I420 buffers and
  returns the image at once; an exhausted pool skips the frame at the source. Zero-copy is an M7 candidate.
- **Timestamp base per session.** A CameraX timestamp within one second of `elapsedRealtimeNanos` on the first frame
  is taken as realtime; otherwise frames are stamped on arrival (ADR-011). `Connected` is sent with the first frame,
  so its `StreamSpec` has the real resolution.
- **Detection model.** EfficientDet-Lite0 int8 (uint8 `[1, 320, 320, 3]` input, TFLite detection post-processing
  outputs), fetched by `scripts/fetch-model.sh` with a pinned SHA-256; not committed. The debug app copies it into
  `filesDir/models/<version>/`. It is **not signed** until the model store of M5.
- **LiteRT 1.4.2** (ADR-005): GPU delegate when `CompatibilityList` supports the device and the interpreter builds
  with it; otherwise CPU with XNNPACK. The model's tensor shapes are checked at load; a warm-up run follows.
- **Rendering.** The screen converts the analysed frame to ARGB on the CPU into two reused bitmaps, inside
  `DisplaySlot.read`, only when the slot holds the annotated frame. Moving this to the GPU is an M7 candidate.
- **Not observed yet.** Memory pressure stays NORMAL until M4; telemetry goes to Logcat until M6.

## Consequences

- Display latency includes the CPU conversion; M7 measures it.
- The golden test (LRT-1) needs a device and the fetched model; its golden file is recorded on the first run.
