package dev.framelock.live.capture.camerax

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.port.trace
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameIdSequence
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.frame.TimestampSource
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * [FrameSource] over CameraX `ImageAnalysis` (the fallback source, works without a capture card).
 *
 * - Each image is copied into a [YuvFramePool] buffer and returned to CameraX at once; when the pool is empty the
 *   image is skipped (ADR-010).
 * - A session starts at the first image after [open]: `Connected` carries the real resolution and timestamp base.
 *   Session ids keep increasing for the lifetime of this source.
 * - Frames are delivered upright: CameraX rotates them to the display rotation, so `Frame.width`/`height` are the
 *   rotated size. A rotation while open changes that size and swaps the pool, but keeps the session.
 * - A camera error ends the session with `Disconnected(STREAM_ERROR)`.
 * - Events that are never collected are released: the channel closes their buffers.
 *
 * The camera binds to a lifecycle this source owns, so the pipeline, not a screen, decides when it runs.
 */
class CameraXFrameSource(
    private val context: Context,
    private val clock: Clock,
    private val tracer: Tracer,
    private val requestedSize: Size = Size(DEFAULT_WIDTH, DEFAULT_HEIGHT),
) : FrameSource {
    private val channel =
        Channel<SourceEvent>(Channel.UNLIMITED) { event ->
            if (event is SourceEvent.FrameAvailable) event.frame.image.close()
        }
    override val events: Flow<SourceEvent> = channel.receiveAsFlow()

    private val lifecycleOwner = SourceLifecycleOwner()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "camera-analysis") }
    private val lastSession = AtomicLong(0)
    private val frameIds = FrameIdSequence()

    // Touched only on the analysis thread, except [session] which open() and close() reset on the main thread.
    @Volatile private var session: SessionId? = null
    private var pool: YuvFramePool? = null
    private var timestampSource = TimestampSource.ARRIVAL
    private var connected = false

    private var provider: ProcessCameraProvider? = null
    private var stateObserver: Observer<CameraState>? = null
    private var analysis: ImageAnalysis? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    override suspend fun open() =
        withContext(Dispatchers.Main) {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider.unbindAll()
            val displayManager = context.getSystemService(DisplayManager::class.java)
            val analysis =
                ImageAnalysis
                    .Builder()
                    .setOutputImageRotationEnabled(true)
                    .setTargetRotation(displayRotation(displayManager))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setResolutionSelector(
                        ResolutionSelector
                            .Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    requestedSize,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            ).build(),
                    ).build()
            session = SessionId(lastSession.incrementAndGet())
            connected = false
            analysis.setAnalyzer(analysisExecutor, ::onImage)
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            val observer = Observer<CameraState> { state -> if (state.error != null) onCameraError() }
            camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)
            stateObserver = observer
            this@CameraXFrameSource.analysis = analysis
            val listener =
                object : DisplayManager.DisplayListener {
                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId ==
                            Display.DEFAULT_DISPLAY
                        ) {
                            analysis.targetRotation = displayRotation(displayManager)
                        }
                    }

                    override fun onDisplayAdded(displayId: Int) = Unit

                    override fun onDisplayRemoved(displayId: Int) = Unit
                }
            displayManager.registerDisplayListener(listener, null)
            displayListener = listener
            provider = cameraProvider
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }

    override suspend fun close() =
        withContext(Dispatchers.Main) {
            session = null
            displayListener?.let { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
            displayListener = null
            analysis = null
            provider?.unbindAll()
            provider = null
            stateObserver = null
            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
            // Frames still queued were never delivered: release them now (ADR-010).
            while (true) {
                val event = channel.tryReceive().getOrNull() ?: break
                if (event is SourceEvent.FrameAvailable) event.frame.image.close()
            }
        }

    private fun onImage(image: ImageProxy) {
        image.use { proxy ->
            val current = session ?: return
            val arrival = clock.nowNanos()
            if (!connected) startSession(current, proxy, arrival)
            // Allocates only when the size changed, i.e. on a rotation: never per frame in steady state.
            pool = poolFor(pool, proxy.width, proxy.height, POOL_CAPACITY)
            val buffer =
                tracer.trace(TraceSection.CAPTURE) {
                    val planes = proxy.planes
                    pool?.copy(
                        SourcePlane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride),
                        SourcePlane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride),
                        SourcePlane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride),
                    )
                } ?: return
            val captured = if (timestampSource == TimestampSource.REALTIME) proxy.imageInfo.timestamp else arrival
            val frame = Frame(frameIds.next(), current, captured, proxy.width, proxy.height, buffer)
            if (channel.trySend(SourceEvent.FrameAvailable(frame)).isFailure) buffer.close()
        }
    }

    private fun startSession(
        current: SessionId,
        proxy: ImageProxy,
        arrival: Long,
    ) {
        timestampSource = TimestampBase.of(proxy.imageInfo.timestamp, arrival)
        frameIds.restart()
        connected = true
        channel.trySend(
            SourceEvent.Connected(
                StreamSpec(proxy.width, proxy.height, ImageFormat.YUV_420_888, timestampSource),
                current,
            ),
        )
    }

    private fun onCameraError() {
        if (session == null) return
        session = null
        connected = false
        channel.trySend(SourceEvent.Disconnected(DisconnectReason.STREAM_ERROR))
    }

    private fun displayRotation(displayManager: DisplayManager): Int =
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    /** Owned lifecycle: the camera runs between open() and close(), whatever the screens do. Main thread only. */
    private class SourceLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }

        override val lifecycle: Lifecycle get() = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private companion object {
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480

        /** Pending, inferring, displayed, plus one being filled (ADR-010). */
        const val POOL_CAPACITY = 4
    }
}
