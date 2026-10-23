package dev.framelock.live.capture.uvc

import dev.framelock.live.domain.frame.ImageFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RgbaFramePoolTest {
    @Test
    fun `copies the pixels into one tightly packed RGBA plane`() {
        val pool = RgbaFramePool(width = 2, height = 1, capacity = 1)
        val pixels = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        val buffer = pool.copy(direct(pixels))!!

        assertEquals(ImageFormat.RGBA_8888, buffer.format)
        assertEquals(1, buffer.planes.size)
        assertEquals(8, buffer.planes[0].rowStride)
        assertEquals(4, buffer.planes[0].pixelStride)
        assertArrayEquals(pixels, bytes(buffer.planes[0].buffer))
    }

    @Test
    fun `a frame of another size is skipped`() {
        val pool = RgbaFramePool(width = 2, height = 1, capacity = 1)

        assertNull(pool.copy(direct(ByteArray(12))))
        assertEquals(1, pool.available)
    }

    @Test
    fun `an exhausted pool gives no buffer instead of allocating`() {
        val pool = RgbaFramePool(width = 1, height = 1, capacity = 1)
        pool.copy(direct(ByteArray(4)))

        assertNull(pool.copy(direct(ByteArray(4))))
    }

    @Test
    fun `closing a buffer returns it to the pool for reuse`() {
        val pool = RgbaFramePool(width = 1, height = 1, capacity = 1)
        val first = pool.copy(direct(ByteArray(4)))!!

        first.close()
        val second = pool.copy(direct(byteArrayOf(9, 9, 9, 9)))

        assertSame(first, second)
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), bytes(second!!.planes[0].buffer))
    }

    @Test
    fun `a double close returns the buffer only once`() {
        val pool = RgbaFramePool(width = 1, height = 1, capacity = 2)
        val buffer = pool.copy(direct(ByteArray(4)))!!

        buffer.close()
        buffer.close()

        assertEquals(2, pool.available)
    }

    @Test
    fun `the same size keeps the pool and a new size swaps it`() {
        val pool = RgbaFramePool(width = 4, height = 2, capacity = 1)

        assertSame(pool, rgbaPoolFor(pool, 4, 2, 1))
        assertNotSame(pool, rgbaPoolFor(pool, 2, 4, 1))
        assertEquals(4, rgbaPoolFor(null, 4, 2, 4).available)
    }

    @Test
    fun `a buffer of a replaced pool returns to that pool`() {
        val old = RgbaFramePool(width = 1, height = 1, capacity = 1)
        val inFlight = old.copy(direct(ByteArray(4)))!!
        val replacement = rgbaPoolFor(old, 2, 1, 1)

        inFlight.close()

        assertEquals(1, old.available)
        assertEquals(1, replacement.available)
    }

    private fun direct(bytes: ByteArray): ByteBuffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes).flip() }

    private fun bytes(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        copy.rewind()
        return ByteArray(copy.remaining()).also { copy.get(it) }
    }
}
