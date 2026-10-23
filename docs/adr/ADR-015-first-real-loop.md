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
- **Detection model.** EfficientDet-Lite0 from Kaggle Models (uint8 `[1, 320, 320, 3]` input, TFLite detection
  post-processing outputs), fetched by `scripts/fetch-model.sh` with pinned SHA-256s; not committed. The MediaPipe
  build of the same model was rejected: it has no post-processing op (two raw anchor outputs), so decoding and NMS
  would have to be written here. The debug app copies it into
  `filesDir/models/<version>/`. It is **not signed** until the model store of M5.
- **LiteRT 1.4.2** (ADR-005): GPU delegate when `CompatibilityList` supports the device and the interpreter builds
  with it; otherwise CPU with XNNPACK. The model's tensor shapes are checked at load; a warm-up run follows.
- **Upright frames.** CameraX rotates the analysis output to the display rotation
  (`setOutputImageRotationEnabled`), and the source follows rotation changes while open. A rotation changes the
  frame size: the buffer pool is replaced (no allocation otherwise) and the session is kept, so the `StreamSpec` of
  `Connected` keeps the first frame's size. The CameraX documentation warns of extra per-frame processing (about
  10–15 ms at 640×480 on a mid-range device); rotating during preprocessing instead is an M7 candidate.
- **Labels.** The model's metadata appends a zip holding `labelmap.txt` to the model bytes, with offsets counted from
  the start of the model file. Android's `ZipFile` rejects such a file (the JDK's accepts it) and `ZipInputStream`
  finds no entry from the start, so the app reads the zip's central directory itself (`EmbeddedLabels`). A failure
  is logged and the overlay falls back to class numbers.
- **Rendering.** The screen converts the analysed frame to ARGB on the CPU into two reused bitmaps, inside
  `DisplaySlot.read`, only when the slot holds the annotated frame. Moving this to the GPU is an M7 candidate.
- **Not observed yet.** Memory pressure stays NORMAL until M4; telemetry goes to Logcat until M6.

## Consequences

- Detection covers the 80 COCO object classes only, with the accuracy of the smallest EfficientDet-Lite model:
  objects outside COCO (tools, wristwatches) are missed or confused. Domain models come with the model store (M5).
- Display latency includes the CPU conversion; M7 measures it.
- The golden test (LRT-1) needs a device and the fetched model; its golden file is recorded on the first run.
