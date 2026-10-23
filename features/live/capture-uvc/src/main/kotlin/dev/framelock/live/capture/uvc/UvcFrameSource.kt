package dev.framelock.live.capture.uvc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.port.trace
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameIdSequence
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.SessionIdSequence
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.frame.TimestampSource
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [FrameSource] over a USB video class device (a capture card), driven through UVCAndroid's low-level API
 * (`USBMonitor` + `UVCCamera`, ADR-016).
 *
 * - Every library call runs on one `HandlerThread` this source owns, created by [open] and ended by [close]. The frame
 *   callback is the exception: the library calls it on its native capture thread.
 * - [open] picks the first attached UVC device and asks for the USB permission. Nothing attached gives
 *   `Disconnected(DEVICE_DETACHED)`; a refusal gives `PERMISSION_DENIED`; a negotiation or open error gives
 *   `OPEN_FAILED`. The pipeline's reconnection calls [open] again after any of them (except a refusal): [open] first
 *   tears down whatever the previous call left, so it can be called without [close] in between.
 * - The mode (format, size, rate) is chosen by [CaptureModeSelector] and logged once per stream, which is the M3
 *   capture-format measurement.
 * - Frames are requested in RGBX, the cheapest format the library offers (every callback format is derived from an
 *   RGBX conversion: MJPEG is decoded by libjpeg-turbo, YUYV converted). Each is copied into an [RgbaFramePool]
 *   buffer inside the callback, since the library recycles its frame when the callback returns (ADR-010); an empty
 *   pool skips the frame. A slow callback makes the library drop frames, so nothing else happens there.
 * - The library gives no capture timestamp: frames are stamped on arrival, [TimestampSource.ARRIVAL].
 * - The library renders every frame to a surface, and does not stream at all without one: a throwaway `ImageReader`
 *   surface takes them (a plain `SurfaceTexture` would stall the library once its buffers fill). Its cost, one extra
 *   copy per frame, is an M7 candidate.
 * - A [FrameWatchdog] ends a stream that goes silent (`SIGNAL_LOST`) or never starts (`OPEN_FAILED`). A card that
 *   keeps sending a placeholder picture when its input is unplugged is not detected; to be checked on the real card.
 * - Any end of a stream tears everything down: callback cleared, preview stopped, camera closed and destroyed, monitor
 *   unregistered and destroyed. A full teardown also mitigates the library's issue 134 (no frames after re-opening on
 *   Android 16).
 * - Events that are never collected are released: the channel closes their buffers.
 */
class UvcFrameSource(
    private val context: Context,
    private val clock: Clock,
    private val tracer: Tracer,
    private val sessionIds: SessionIdSequence,
    targetWidth: Int = DEFAULT_WIDTH,
    targetHeight: Int = DEFAULT_HEIGHT,
    private val signalTimeout: Duration = 2.seconds,
    private val startTimeout: Duration = 5.seconds,
) : FrameSource {
    private val channel =
        Channel<SourceEvent>(Channel.UNLIMITED) { event ->
            if (event is SourceEvent.FrameAvailable) event.frame.image.close()
        }
    override val events: Flow<SourceEvent> = channel.receiveAsFlow()

    private val selector = CaptureModeSelector(targetWidth, targetHeight)
    private val usbManager = context.getSystemService(UsbManager::class.java)

    /** Serialises [open] and [close]; the thread fields below are only touched under it. */
    private val lifecycle = Mutex()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    // Only touched on the handler thread, except [pool], which the capture thread reads while a stream lives.
    private var current: Stream? = null

    @Volatile private var pool: RgbaFramePool? = null

    override suspend fun open() {
        lifecycle.withLock {
            val target = handler ?: startThread()
            target.call {
                current?.end(null)
                val device = UvcDevices.firstAttached(context)
                if (device == null) {
                    channel.trySend(SourceEvent.Disconnected(DisconnectReason.DEVICE_DETACHED))
                } else {
                    Stream(target, device).also { current = it }.begin()
                }
            }
        }
    }

    override suspend fun close() {
        lifecycle.withLock {
            val target = handler ?: return@withLock
            withContext(NonCancellable) { target.call { current?.end(null) } }
            thread?.quitSafely()
            thread = null
            handler = null
            // Frames still queued were never delivered: release them now (ADR-010).
            while (true) {
                val event = channel.tryReceive().getOrNull() ?: break
                if (event is SourceEvent.FrameAvailable) event.frame.image.close()
            }
        }
    }

    private fun startThread(): Handler {
        val created = HandlerThread("uvc-source").also { it.start() }
        thread = created
        return Handler(created.looper).also { handler = it }
    }

    /** Runs [block] on this handler's thread and returns its result (or throws its exception) to the caller. */
    private suspend fun <T> Handler.call(block: () -> T): T =
        suspendCancellableCoroutine { continuation ->
            val posted =
                post {
                    try {
                        continuation.resume(block())
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        continuation.resumeWithException(e)
                    }
                }
            if (!posted) continuation.resumeWithException(IllegalStateException("The UVC thread has ended"))
        }

    /**
     * One attempt to stream from [device], from the permission request to the end of the stream. A stream that has
     * [ended] ignores every later callback, including those of the library for the objects it released.
     */
    private inner class Stream(
        private val handler: Handler,
        private val device: UsbDevice,
    ) : USBMonitor.OnDeviceConnectListener,
        IFrameCallback {
        private val monitor = USBMonitor(context, this, handler)
        private val watchdog = FrameWatchdog(clock, signalTimeout, startTimeout)
        private val frameIds = FrameIdSequence()
        private var camera: UVCCamera? = null
        private var reader: ImageReader? = null
        private var permissionReceiver: BroadcastReceiver? = null
        private var ended = false

        // Set by the handler thread, read by the capture thread.
        @Volatile private var live = false

        @Volatile private var width = 0

        @Volatile private var height = 0

        // Capture thread only.
        private var session: SessionId? = null
        private var warnedSize = false

        private val checkWatchdog =
            object : Runnable {
                override fun run() {
                    if (ended) return
                    when (watchdog.check()) {
                        WatchdogVerdict.OK -> Unit
                        WatchdogVerdict.NEVER_STARTED -> end(DisconnectReason.OPEN_FAILED)
                        WatchdogVerdict.SIGNAL_LOST -> end(lostReason())
                    }
                    if (!ended) handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }

        /** Registers the monitor, which is the only way the library hands out the control block it needs. */
        fun begin() {
            monitor.register()
            if (usbManager.hasPermission(device)) monitor.requestPermission(device) else requestPermission()
        }

        /**
         * The permission is asked here, with an explicit intent and a receiver that is not exported, rather than
         * through the library's implicit mutable intent. Once granted, the library opens the device (ADR-016).
         */
        private fun requestPermission() {
            val action = "$PERMISSION_ACTION_PREFIX${hashCode()}"
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (ended) return
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            monitor.requestPermission(device)
                        } else {
                            end(DisconnectReason.PERMISSION_DENIED)
                        }
                    }
                }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                null,
                handler,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            permissionReceiver = receiver
            val request =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_MUTABLE,
                )
            usbManager.requestPermission(device, request)
        }

        // --- USBMonitor callbacks, on the handler thread ---

        override fun onDeviceOpen(
            device: UsbDevice,
            ctrlBlock: USBMonitor.UsbControlBlock,
            createNew: Boolean,
        ) {
            if (ended || camera != null) return
            startStreaming(ctrlBlock)
        }

        override fun onCancel(device: UsbDevice) = end(DisconnectReason.PERMISSION_DENIED)

        override fun onError(
            device: UsbDevice,
            e: USBMonitor.USBException,
        ) {
            Log.w(TAG, "USB open error ${e.code}: ${e.message}")
            end(DisconnectReason.OPEN_FAILED)
        }

        override fun onDeviceClose(
            device: UsbDevice,
            ctrlBlock: USBMonitor.UsbControlBlock,
        ) = end(DisconnectReason.DEVICE_DETACHED)

        override fun onDetach(device: UsbDevice) = end(DisconnectReason.DEVICE_DETACHED)

        override fun onAttach(device: UsbDevice) = Unit

        private fun startStreaming(ctrlBlock: USBMonitor.UsbControlBlock) {
            val uvc = UVCCamera(UVCParam())
            camera = uvc
            if (uvc.open(ctrlBlock) != 0) {
                Log.w(TAG, "UVCCamera.open failed")
                end(DisconnectReason.OPEN_FAILED)
                return
            }
            val offered = uvc.supportedSizeList.mapNotNull(::supportedModeOf)
            val mode = selector.select(offered)
            if (mode == null) {
                Log.w(TAG, "No usable capture mode among ${uvc.supportedSizeList}")
                end(DisconnectReason.OPEN_FAILED)
                return
            }
            try {
                uvc.setPreviewSize(mode.width, mode.height, frameTypeOf(mode.format), mode.fps)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Library refused $mode", e)
                end(DisconnectReason.OPEN_FAILED)
                return
            }
            Log.i(TAG, "Capture mode: ${mode.format} ${mode.width}x${mode.height}@${mode.fps} (offered: $offered)")
            val images = ImageReader.newInstance(mode.width, mode.height, PixelFormat.RGBA_8888, READER_IMAGES)
            images.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
            reader = images
            uvc.setPreviewDisplay(images.surface)
            width = mode.width
            height = mode.height
            pool = rgbaPoolFor(pool, mode.width, mode.height, POOL_CAPACITY)
            uvc.setFrameCallback(this, UVCCamera.PIXEL_FORMAT_RGBX)
            live = true
            watchdog.start()
            uvc.startPreview()
            handler.postDelayed(checkWatchdog, CHECK_INTERVAL_MS)
        }

        // --- Frame callback, on the library's capture thread ---

        override fun onFrame(frame: ByteBuffer) {
            if (!live) return
            val arrival = clock.nowNanos()
            watchdog.onFrame()
            val target = pool ?: return
            val buffer = tracer.trace(TraceSection.CAPTURE) { target.copy(frame) }
            if (buffer == null) {
                warnOnUnexpectedSize(target, frame)
                return
            }
            val id = session ?: startSession()
            val delivered = Frame(frameIds.next(), id, arrival, width, height, buffer)
            if (channel.trySend(SourceEvent.FrameAvailable(delivered)).isFailure) buffer.close()
        }

        private fun startSession(): SessionId {
            val id = sessionIds.next()
            session = id
            frameIds.restart()
            channel.trySend(
                SourceEvent.Connected(StreamSpec(width, height, ImageFormat.RGBA_8888, TimestampSource.ARRIVAL), id),
            )
            return id
        }

        /** A pool that is not empty refused the frame: its size is not the negotiated one. */
        private fun warnOnUnexpectedSize(
            target: RgbaFramePool,
            frame: ByteBuffer,
        ) {
            if (target.available > 0 && !warnedSize) {
                warnedSize = true
                Log.w(TAG, "Frame of ${frame.remaining()} bytes, expected ${width * height * 4}: frames are skipped")
            }
        }

        // --- End of the stream, on the handler thread ---

        /** Why a silent stream is over: the device may simply be gone, without the library having said so yet. */
        private fun lostReason(): DisconnectReason =
            if (UvcDevices.firstAttached(context) == null) {
                DisconnectReason.DEVICE_DETACHED
            } else {
                DisconnectReason.SIGNAL_LOST
            }

        /**
         * Releases everything, then reports [reason] unless it is null (a teardown asked by [open] or [close]).
         * Safe to call twice.
         */
        fun end(reason: DisconnectReason?) {
            if (ended) return
            ended = true
            live = false
            watchdog.stop()
            handler.removeCallbacks(checkWatchdog)
            release()
            if (current === this) current = null
            if (reason != null) channel.trySend(SourceEvent.Disconnected(reason))
        }

        @Suppress("TooGenericExceptionCaught")
        private fun release() {
            permissionReceiver?.let { attempt { context.unregisterReceiver(it) } }
            permissionReceiver = null
            camera?.let { uvc ->
                // The library keeps the callback after stopPreview: clear it first.
                attempt { uvc.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_RGBX) }
                attempt { uvc.stopPreview() }
                attempt { uvc.destroy() }
            }
            camera = null
            reader?.let { attempt { it.close() } }
            reader = null
            attempt { monitor.unregister() }
            attempt { monitor.destroy() }
        }

        @Suppress("TooGenericExceptionCaught")
        private inline fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "Teardown step failed", e)
            }
        }
    }

    private fun supportedModeOf(size: Size): SupportedMode? {
        val format =
            when (size.type) {
                UVCCamera.UVC_VS_FRAME_UNCOMPRESSED -> CaptureFormat.YUYV
                UVCCamera.UVC_VS_FRAME_MJPEG -> CaptureFormat.MJPEG
                else -> return null
            }
        val rates = (size.fpsList.orEmpty() + size.fps).filter { it > 0 }.distinct()
        return SupportedMode(format, size.width, size.height, rates)
    }

    private fun frameTypeOf(format: CaptureFormat): Int =
        when (format) {
            CaptureFormat.YUYV -> UVCCamera.UVC_VS_FRAME_UNCOMPRESSED
            CaptureFormat.MJPEG -> UVCCamera.UVC_VS_FRAME_MJPEG
        }

    private companion object {
        const val TAG = "UvcFrameSource"
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val PERMISSION_ACTION_PREFIX = "dev.framelock.live.capture.uvc.USB_PERMISSION."
        const val CHECK_INTERVAL_MS = 500L

        /** Pending, inferring, displayed, plus one being filled (ADR-010). */
        const val POOL_CAPACITY = 4

        /** Images the throwaway surface may hold; each is closed as soon as it is available. */
        const val READER_IMAGES = 2
    }
}
