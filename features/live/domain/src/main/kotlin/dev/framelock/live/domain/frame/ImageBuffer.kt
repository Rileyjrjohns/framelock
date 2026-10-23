package dev.framelock.live.domain.frame

import java.nio.ByteBuffer

enum class ImageFormat { YUV_420_888, RGBA_8888, JPEG }

class Plane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/** Pixels of a frame, taken from a pool. [close] returns them to the pool; it must be called exactly once. */
interface ImageBuffer : AutoCloseable {
    val format: ImageFormat
    val planes: List<Plane>

    override fun close()
}
