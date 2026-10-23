package dev.framelock.live.capture.uvc

import dev.framelock.live.domain.frame.ImageBuffer
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A fixed pool of tightly packed RGBA_8888 buffers (ADR-010, ADR-012, ADR-016). The library recycles its frame as soon
 * as the callback returns, so each frame is copied into a pooled buffer. Nothing is allocated per frame: when every
 * buffer is in use, [copy] returns null and the frame is skipped at the source.
 */
internal class RgbaFramePool(
    val width: Int,
    val height: Int,
    capacity: Int,
) {
    private val free = ArrayBlockingQueue<PooledRgbaBuffer>(capacity)
    private val frameBytes = width * height * BYTES_PER_PIXEL

    init {
        require(width > 0 && height > 0) { "Positive size: ${width}x$height" }
        repeat(capacity) { free.add(PooledRgbaBuffer(width, height, this)) }
    }

    /** Buffers ready for a frame. */
    val available: Int get() = free.size

    /**
     * Copies the remaining bytes of [pixels] (advancing it) into a pooled buffer. Null when the pool is exhausted or
     * [pixels] does not hold exactly one frame of this pool's size.
     */
    fun copy(pixels: ByteBuffer): ImageBuffer? {
        if (pixels.remaining() != frameBytes) return null
        val target = free.poll() ?: return null
        target.fill(pixels)
        return target
    }

    internal fun release(buffer: PooledRgbaBuffer) {
        free.offer(buffer)
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}

internal class PooledRgbaBuffer(
    width: Int,
    height: Int,
    private val pool: RgbaFramePool,
) : ImageBuffer {
    private val pixels = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL)
    private val inUse = AtomicBoolean(false)

    override val format = ImageFormat.RGBA_8888
    override val planes = listOf(Plane(pixels, rowStride = width * BYTES_PER_PIXEL, pixelStride = BYTES_PER_PIXEL))

    fun fill(source: ByteBuffer) {
        inUse.set(true)
        pixels.clear()
        pixels.put(source)
        pixels.flip()
    }

    /** Returns the buffer to its pool; a second close is ignored. */
    override fun close() {
        if (inUse.compareAndSet(true, false)) pool.release(this)
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}

/** The pool for frames of [width] x [height]: [current] when it already has that size, else a new one. */
internal fun rgbaPoolFor(
    current: RgbaFramePool?,
    width: Int,
    height: Int,
    capacity: Int,
): RgbaFramePool =
    current?.takeIf { it.width == width && it.height == height } ?: RgbaFramePool(width, height, capacity)
