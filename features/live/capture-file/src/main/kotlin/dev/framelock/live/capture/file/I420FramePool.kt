package dev.framelock.live.capture.file

import dev.framelock.live.domain.frame.ImageBuffer
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** One plane of a decoded image: its bytes and how they are laid out. */
internal class SourcePlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/**
 * A fixed pool of tightly packed I420 buffers (ADR-010, ADR-012). Each decoded image is copied into a pooled buffer and
 * its codec buffer released at once, so the pipeline can hold frames (pending, inferring, displayed) without starving
 * the decoder. Nothing is allocated per frame: when every buffer is in use, [copy] returns null and the frame is
 * skipped at the source. This is a minimal copy of the pool of `:features:live:capture-camerax`: adapters cannot
 * depend on each other (ADR-014).
 */
internal class I420FramePool(
    val width: Int,
    val height: Int,
    capacity: Int,
) {
    private val free = ArrayBlockingQueue<PooledI420Buffer>(capacity)

    /** Scratch and cached views shared by every buffer of this pool; frames are copied one at a time. */
    internal val copier = PlaneCopier()

    init {
        require(
            width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0,
        ) { "Even, positive size: ${width}x$height" }
        repeat(capacity) { free.add(PooledI420Buffer(width, height, this)) }
    }

    /** Buffers ready for a frame. */
    val available: Int get() = free.size

    /** Copies the three planes into a pooled buffer; null when the pool is exhausted. */
    fun copy(
        y: SourcePlane,
        u: SourcePlane,
        v: SourcePlane,
    ): ImageBuffer? {
        val target = free.poll() ?: return null
        target.fill(y, u, v)
        return target
    }

    internal fun release(buffer: PooledI420Buffer) {
        free.offer(buffer)
    }
}

internal class PooledI420Buffer(
    private val width: Int,
    private val height: Int,
    private val pool: I420FramePool,
) : ImageBuffer {
    private val y = ByteBuffer.allocateDirect(width * height)
    private val u = ByteBuffer.allocateDirect(width * height / CHROMA_DIVISOR)
    private val v = ByteBuffer.allocateDirect(width * height / CHROMA_DIVISOR)
    private val inUse = AtomicBoolean(false)

    override val format = ImageFormat.YUV_420_888
    override val planes =
        listOf(
            Plane(y, rowStride = width, pixelStride = 1),
            Plane(u, rowStride = width / 2, pixelStride = 1),
            Plane(v, rowStride = width / 2, pixelStride = 1),
        )

    fun fill(
        source: SourcePlane,
        uSource: SourcePlane,
        vSource: SourcePlane,
    ) {
        inUse.set(true)
        pool.copier.copy(source, y, width, height)
        pool.copier.copy(uSource, u, width / 2, height / 2)
        pool.copier.copy(vSource, v, width / 2, height / 2)
    }

    /** Returns the buffer to its pool; a second close is ignored. */
    override fun close() {
        if (inUse.compareAndSet(true, false)) pool.release(this)
    }

    private companion object {
        const val CHROMA_DIVISOR = 4
    }
}

/**
 * The pool to use for images of [width] x [height]: [current] when it already has that size, else a new one. The
 * replaced pool is simply dropped: buffers still in flight keep a reference to it and return to it on close, then
 * everything is garbage collected together.
 */
internal fun poolFor(
    current: I420FramePool?,
    width: Int,
    height: Int,
    capacity: Int,
): I420FramePool =
    current?.takeIf { it.width == width && it.height == height } ?: I420FramePool(width, height, capacity)
