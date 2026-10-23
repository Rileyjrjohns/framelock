# ADR-016: UVC capture (M3) choices

- Status: Accepted
- Date: 2026-10-23

## Context

M3 adds the capture card as a source. The test device (Galaxy A33, Android 16) has `android.hardware.usb.host` but
not `android.hardware.camera.external`, so Camera2 does not expose the card there: it must be driven through a UVC
library. Several choices depend on what that library does, which was read in its source and checked against the
1.0.13 binary.

## Decision

- **Library: UVCAndroid 1.0.13** (`com.herohan:UVCAndroid`, Maven Central, Apache-2.0, 16 KB aligned 64-bit native
  libraries; `zipalign -c -P 16` passes on the debug APK). Its high-level `CameraHelper` always creates its own GL
  renderer, so the adapter uses the low-level API: `USBMonitor` and `UVCCamera`. The source first tries Camera2
  external cameras (`LENS_FACING_EXTERNAL`, behind CameraX's `ExperimentalLensFacing` opt-in) and falls back to the
  library when the device does not expose the card that way.
- **Frames are requested as RGBX and delivered as `RGBA_8888`.** Every callback format of the library is derived from
  an RGBX conversion (MJPEG decoded by libjpeg-turbo, YUYV converted), so RGBX is the cheapest. The callback copies
  the frame into one of four preallocated buffers (`RgbaFramePool`, ADR-010) because the library recycles its frame
  when the callback returns; an empty pool skips the frame. Consumers (inference, screen) read `RGBA_8888` in addition
  to I420.
- **No capture timestamp.** The library gives none: frames are stamped on arrival from the `Clock` port
  (`TimestampSource.ARRIVAL`, ADR-011). Latency measured from it excludes the USB and decode stages.
- **A surface is mandatory.** The native `startPreview` creates its thread only if a preview window is set, so
  streaming without a surface does nothing. The adapter gives it a throwaway `ImageReader` surface and closes each
  image at once. A plain `SurfaceTexture` would stall the library once its buffers are full, since nobody consumes
  them. The library writes every frame to that surface: one extra copy per frame (M7 candidate, to measure).
- **Permission and receiver.** The library hands out the `UsbControlBlock` that `UVCCamera.open` needs only through
  `USBMonitor.requestPermission`, which works only after `register()`: `UsbControlBlock`'s constructor is private and
  `USBMonitor` has no public open method. So `register()` is unavoidable. It registers a receiver as
  `RECEIVER_EXPORTED` on API 33+ (for the library's permission action and `USB_DEVICE_DETACHED`) and a mutable
  `PendingIntent` with an implicit intent (`FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT` on API 34+). Another app could
  therefore send a fake detach or permission broadcast to that receiver. The consequence is limited: a fake grant
  still fails at `UsbManager.openDevice` without the real permission, and a fake detach only ends the stream, which
  the reconnection restores. The adapter does not use the library's `PendingIntent`: it asks for the permission
  itself with `UsbManager.requestPermission`, an explicit intent (`setPackage`), `FLAG_MUTABLE` as the system fills
  the extras, and a receiver that is not exported. Once granted, it calls `USBMonitor.requestPermission`, which finds
  the permission and opens the device. The exported receiver stays a library behaviour, recorded here, not hidden.
- **Which device.** The selected UVC device, else (Auto) the first attached one, by name: the device class or one of its interfaces is video
  (0x0E). The manifest also declares a `USB_DEVICE_ATTACHED` filter for that class, so Android can grant the
  permission by default when the user plugs the card in (`launchMode="singleTop"` keeps one activity).
- **Capture mode (`CaptureModeSelector`, pure).** Size first: the offered size closest to 1280x720 by pixel count (a
  tie goes to the larger). Then at that size: YUYV at the lowest rate that reaches 30 fps (no JPEG to decode; a faster
  stream only costs bandwidth, since the pipeline conflates frames); else MJPEG at the lowest rate reaching 30 fps (a
  60 fps stream would only add JPEG decoding: on a test webcam the pipeline analysed about 14 frames per second); else
  MJPEG, then YUYV, at its highest rate. The library treats every uncompressed format as YUYV: a card offering only
  NV12 would be mislabeled.
  The chosen mode and the offered ones are logged once per stream (`Log.i`, tag `UvcFrameSource`): this is the M3
  capture-format measurement. Input fps is on the HUD.
- **No-signal watchdog (`FrameWatchdog`, pure, `Clock` port).** The library cannot tell that the HDMI input is
  absent. Once frames flow, 2 s without a frame ends the stream with `Disconnected(SIGNAL_LOST)`; 5 s after the
  preview started without a first frame ends it with `OPEN_FAILED`. `SIGNAL_LOST` is a new `DisconnectReason`: the
  pipeline shows `NoSignal` and retries it like `DEVICE_DETACHED`. If the device is gone when the watchdog fires,
  the reason is `DEVICE_DETACHED`. A card that keeps sending a placeholder picture without input is not detected.
- **Teardown.** Any end of a stream releases everything: frame callback cleared (the library does not clear it in
  `stopPreview`), preview stopped, camera closed and destroyed, monitor unregistered and destroyed. `open()` tears
  down the previous stream first, since the pipeline's reconnection calls `open()` again without `close()`. All
  library calls run on one `HandlerThread` owned by the source, except the frame callback, which runs on the
  library's native thread.
- **Source selection (`SelectingFrameSource`, in `:app`).** Only `:app` sees every adapter. It forwards to one of four
  sources (external CameraX lens, UVC, back camera, front camera), chosen when a session opens from the user's choice
  (`SourceSelection`). `Auto`, the default, keeps the order: an external camera exposed by Camera2, else an attached
  UVC device, else the back camera. An explicit choice opens that source; for UVC it names the device, which
  `UvcFrameSource` reads through an injected `selectedDeviceId` provider (absent at open: `Disconnected(DEVICE_DETACHED)`,
  retried as before). The choice stays until `close()`, so reconnections reach the same source and a new choice applies
  at the next start; the picker is enabled only while the pipeline is idle. Its `events` merge those of the four sources.
- **Device identity.** The picker lists UVC devices afresh each time it opens, labelled with `UsbDevice.productName`,
  else `manufacturerName`, else "USB video device". The id is `vendorId:productId` in hex. The serial number would
  separate identical units, but `UsbDevice.getSerialNumber()` throws `SecurityException` for an app targeting API 29+
  without permission on the device (https://developer.android.com/reference/android/hardware/usb/UsbDevice#getSerialNumber()),
  and the list is shown before any permission is asked. Identical units therefore get `#2`, `#3` suffixes in
  device-name order; which physical unit holds the plain id can change after a re-plug.
- **Active source in the HUD.** `SelectingFrameSource.activeDescription` (a `StateFlow<String?>`) gives the chosen
  camera's label or, for UVC, `UvcFrameSource.description` (`<device> · <format> <w>x<h>@<fps>`, set where the mode is
  logged, cleared when the stream ends). UI text only, built once per stream; it never reaches telemetry (TEL-2).
- **Amendment to ADR-006 (ports).** The Konsist rule allowed no port implementation in `:app`. A source that chooses
  among adapters can only live there, since adapters never see each other. The rule now lists the composites the app
  may host (`Rules.APP_COMPOSITES`: `SelectingFrameSource` implements `FrameSource`); any other port implementation
  in the app is still a violation, and a fixture checks both cases. Adding a composite is a reviewed code change.
- **Session ids are shared.** One `SessionIdSequence` (live:domain, thread-safe) is injected into every source, so a
  switch of source never gives the pipeline a non-increasing id. CameraX no longer keeps its own counter.
- **Pipeline fix.** A `Disconnected` raised while a reconnection's `open()` was running was ignored, because the
  reconnection loop counted that `open()` as a success and ended. The loop now continues (with its backoff) when a
  `Disconnected` arrived during `open()`. This case is the normal one for a missing device.
- **Manifest.** The merged library manifest asks for audio, storage and foreground-service permissions; the app
  removes them (`tools:node="remove"`) and keeps `CAMERA`, which UVC needs on Android 10+. `usb.host` is declared
  optional, so the back camera still works on a device without a USB host.

## Known library issues (to check on the real card)

- #134: on Android 16, the frame callback never fires after re-opening. Mitigated by the full teardown; to verify by
  unplugging and replugging the card.
- #135: some HDMI capture cards fail with "could not negotiate with camera: err = -51". The preview thread stops; the
  start timeout then ends the stream with `OPEN_FAILED`.
- #49: a card (ms2130) stays black after a format switch until unplugged. The adapter chooses one mode per stream and
  never switches within it.
- To measure: which mode the card offers and gets (MJPEG or YUYV at 1280x720), the real input fps, the cost of the
  extra surface copy, whether the card keeps sending frames without an HDMI input, and the behaviour of the exported
  receiver on Android 16.

## Consequences

- The app ships the library's native libraries (libusb under LGPL-2.1 as a shared object, see `docs/soup.md`) and its
  transitive AndroidX support libraries.
- Frames reach the model as RGBA: the preprocessing for them lives in the inference adapter, and the screen draws
  them without conversion.
- A source chosen at start does not follow later hot plugging between sources: stop and start again.
