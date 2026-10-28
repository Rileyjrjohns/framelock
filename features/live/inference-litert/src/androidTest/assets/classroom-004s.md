# Detection fixture

`classroom-004s.png` and `classroom-004s.i420` contain frame 120 (zero-based, 4.000 s) of the fixed
`classroom.mp4` described in [soup.md](../../../../../../docs/soup.md#tests-only).
They are packaged only in the instrumentation test APK.

Source: [classroom.mp4 at the pinned revision](https://github.com/intel-iot-devkit/sample-videos/blob/57978890822836f2b4743852f04f62fc511757e4/classroom.mp4).
Attribution: classroom.mp4, sample-videos, Intel Corporation, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The [source licence](https://github.com/intel-iot-devkit/sample-videos/blob/57978890822836f2b4743852f04f62fc511757e4/LICENSE)
was checked on 2026-10-28. Modifications: one frame extracted, resized from 1920 × 1080 to 640 × 360,
converted to full-range BT.601 planar YUV 4:2:0, then decoded to RGB PNG. No crop or overlay.

## References

The boxes were annotated by inspecting the PNG before reading inference results. Coordinates describe the
visible extent of each person; the desk occludes the standing person's legs. They are deliberately a subset
of the scene, not an exhaustive annotation of every object.

| Person | Left | Top | Right | Bottom | Model label |
|---|---|---|---|---|---|
| Seated, foreground left | 61 | 189 | 154 | 299 | 0 (`person`) |
| Standing, right | 374 | 137 | 468 | 289 | 0 (`person`) |

`ClassroomFixture` normalizes these pixel coordinates by 640 and 360. Each reference needs a distinct detection
of the same class with score ≥ 0.5 and intersection-over-union ≥ 0.8. Extra detections and ordering are ignored;
scores are not compared to recorded values. There is no generated candidate to approve.

`LiteRtGoldenTest` checks forced CPU and forced GPU separately, using both RGBA and YUV inputs. An unavailable
GPU fails instead of skipping or falling back. The object-level check complements the exact colour and stride
checks in `PreprocessReferenceTest`, `RgbaToRgbTest` and `YuvToRgbTest`; a detector may tolerate a colour error.

Run after fetching the pinned model with `scripts/fetch-model.sh` and attaching a GPU-capable device:

```bash
./gradlew :features:live:inference-litert:connectedDebugAndroidTest
```

## Reproduction

The source video remains outside the repository. With FFmpeg 7.1.1 and `input.mp4` in the working directory:

```bash
ffmpeg -hide_banner -loglevel error -i input.mp4 \
  -vf 'select=eq(n\,120),scale=640:360:flags=lanczos:in_range=pc:out_range=pc:in_color_matrix=bt601:out_color_matrix=bt601' \
  -frames:v 1 -pix_fmt yuvj420p -f rawvideo classroom-004s.i420
ffmpeg -hide_banner -loglevel error -f rawvideo -pixel_format yuvj420p -video_size 640x360 \
  -i classroom-004s.i420 -frames:v 1 classroom-004s.png
```

The I420 file contains Y (230400 bytes), then U and V (57600 bytes each), without headers or padding.
The YUV asset is generated independently of the production converter, so both input paths have their own oracle.

| File | SHA-256 |
|---|---|
| Source `input.mp4` | `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31` |
| `classroom-004s.i420` | `c148123ec335e62ecd18df2200f072df96c984110315f283155c55fff1428d67` |
| `classroom-004s.png` | `cc07e044d98040560245261d860820576d9e487e59c400609ac975c2e339dc6f` |
| `efficientdet_lite0.tflite` | `2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b` |
