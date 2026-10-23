package dev.framelock.live.capture.camerax

import dev.framelock.live.domain.frame.ImageFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class YuvFramePoolTest {
    @Test
    fun `copies padded planes into tight I420 planes`() {
        val pool = YuvFramePool(width = 4, height = 2, capacity = 2)
        // Y: 4x2 with a row stride of 6 (2 bytes of padding per row).
        val y = plane(byteArrayOf(1, 2, 3, 4, 0, 0, 5, 6, 7, 8, 0, 0))
        // U and V: 2x1, interleaved like NV21-backed planes (pixel stride 2).
        val u = plane(byteArrayOf(10, 99, 11, 99))
        val v = plane(byteArrayOf(20, 99, 21, 99))

        val buffer = pool.copy(SourcePlane(y, rowStride = 6, pixelStride = 1), SourcePlane(u, 4, 2), SourcePlane(v, 4, 2))

        assertEquals(ImageFormat.YUV_420_888, buffer?.format)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), bytes(buffer!!.planes[0].buffer))
        assertArrayEquals(byteArrayOf(10, 11), bytes(buffer.planes[1].buffer))
        assertArrayEquals(byteArrayOf(20, 21), bytes(buffer.planes[2].buffer))
        assertEquals(4, buffer.planes[0].rowStride)
        assertEquals(1, buffer.planes[1].pixelStride)
    }

    @Test
    fun `an exhausted pool gives no buffer instead of allocating`() {
        val pool = YuvFramePool(width = 2, height = 2, capacity = 1)
        val first = copyTiny(pool)

        val second = copyTiny(pool)

        assertNotSame(null, first)
        assertNull(second)
    }

    @Test
    fun `closing a buffer returns it to the pool for reuse`() {
        val pool = YuvFramePool(width = 2, height = 2, capacity = 1)
        val first = copyTiny(pool)!!

        first.close()
        val second = copyTiny(pool)

        assertSame(first, second)
        assertEquals(0, pool.available)
    }

    @Test
    fun `a double close returns the buffer only once`() {
        val pool = YuvFramePool(width = 2, height = 2, capacity = 2)
        val buffer = copyTiny(pool)!!

        buffer.close()
        buffer.close()

        assertEquals(2, pool.available)
    }

    private fun copyTiny(pool: YuvFramePool) =
        pool.copy(
            SourcePlane(plane(ByteArray(4)), rowStride = 2, pixelStride = 1),
            SourcePlane(plane(ByteArray(1)), rowStride = 1, pixelStride = 1),
            SourcePlane(plane(ByteArray(1)), rowStride = 1, pixelStride = 1),
        )

    private fun plane(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes)

    private fun bytes(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        copy.rewind()
        return ByteArray(copy.remaining()).also { copy.get(it) }
    }
}
