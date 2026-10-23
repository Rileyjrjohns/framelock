package dev.framelock.live.ui

import dev.framelock.live.domain.frame.Plane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RgbaToArgbTest {
    @Test
    fun `channels are reordered and alpha is forced opaque`() {
        val buffer = ByteBuffer.allocate(4).apply { put(byteArrayOf(0x11, 0x22, 0x33, 0x00)) }
        val out = IntArray(1)

        RgbaToArgb.convert(Plane(buffer, rowStride = 4, pixelStride = 4), width = 1, height = 1, out)

        assertEquals(0xFF112233.toInt(), out[0])
    }

    @Test
    fun `bytes above 127 are not sign extended`() {
        val buffer = ByteBuffer.allocate(4).apply { put(byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x01, 0x55)) }
        val out = IntArray(1)

        RgbaToArgb.convert(Plane(buffer, rowStride = 4, pixelStride = 4), width = 1, height = 1, out)

        assertEquals(0xFFFF8001.toInt(), out[0])
    }

    @Test
    fun `row stride padding is skipped`() {
        // width 1, rowStride 8: each row is one pixel followed by 4 padding bytes.
        val bytes = listOf(1, 2, 3, 0, 9, 9, 9, 9, 7, 8, 9, 0, 9, 9, 9, 9)
        val buffer = ByteBuffer.allocate(bytes.size)
        bytes.forEachIndexed { i, v -> buffer.put(i, v.toByte()) }
        val out = IntArray(2)

        RgbaToArgb.convert(Plane(buffer, rowStride = 8, pixelStride = 4), width = 1, height = 2, out)

        assertEquals(listOf(0xFF010203.toInt(), 0xFF070809.toInt()), out.toList())
    }
}
