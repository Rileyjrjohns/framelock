package dev.framelock.live.capture.camerax

import dev.framelock.live.domain.frame.ImageBuffer
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** One plane of a camera image: its bytes and how they are laid out. */
internal class SourcePlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/**
 * A fixed pool of tightly packed I420 buffers (ADR-010, ADR-012). Each camera image is copied into a pooled buffer and
 * handed back to the camera at once, so the pipeline can hold frames (pending, inferring, displayed) without starving
 * the camera. Nothing is allocated per frame: when every buffer is in use, [copy] returns null and the frame is
 * skipped at the source. Zero-copy is a performance candidate for M7.
 */
internal class YuvFramePool(
    val width: Int,
    val height: Int,
    capacity: Int,
) {
    private val free = ArrayBlockingQueue<PooledYuvBuffer>(capacity)

    init {
        require(
            width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0,
        ) { "Even, positive size: ${width}x$height" }
        repeat(capacity) { free.add(PooledYuvBuffer(width, height, this)) }
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

    internal fun release(buffer: PooledYuvBuffer) {
        free.offer(buffer)
    }
}

internal class PooledYuvBuffer(
    private val width: Int,
    private val height: Int,
    private val pool: YuvFramePool,
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
        copyPlane(source, y, width, height)
        copyPlane(uSource, u, width / 2, height / 2)
        copyPlane(vSource, v, width / 2, height / 2)
    }

    /** Returns the buffer to its pool; a second close is ignored. */
    override fun close() {
        if (inUse.compareAndSet(true, false)) pool.release(this)
    }

    private fun copyPlane(
        source: SourcePlane,
        target: ByteBuffer,
        columns: Int,
        rows: Int,
    ) {
        target.clear()
        val bytes = source.buffer
        val start = bytes.position()
        for (row in 0 until rows) {
            val rowStart = start + row * source.rowStride
            if (source.pixelStride == 1) {
                val slice = bytes.duplicate()
                slice.position(rowStart)
                slice.limit(rowStart + columns)
                target.put(slice)
            } else {
                for (column in 0 until columns) target.put(bytes.get(rowStart + column * source.pixelStride))
            }
        }
        target.flip()
    }

    private companion object {
        const val CHROMA_DIVISOR = 4
    }
}
