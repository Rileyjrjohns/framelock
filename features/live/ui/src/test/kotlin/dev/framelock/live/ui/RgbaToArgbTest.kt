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

        RgbaToArgb().convert(Plane(buffer, rowStride = 4, pixelStride = 4), width = 1, height = 1, out)

        assertEquals(0xFF112233.toInt(), out[0])
    }

    @Test
    fun `bytes above 127 are not sign extended`() {
        val buffer = ByteBuffer.allocate(4).apply { put(byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x01, 0x55)) }
        val out = IntArray(1)

        RgbaToArgb().convert(Plane(buffer, rowStride = 4, pixelStride = 4), width = 1, height = 1, out)

        assertEquals(0xFFFF8001.toInt(), out[0])
    }

    @Test
    fun `row stride padding is skipped`() {
        // width 1, rowStride 8: each row is one pixel followed by 4 padding bytes.
        val bytes = listOf(1, 2, 3, 0, 9, 9, 9, 9, 7, 8, 9, 0, 9, 9, 9, 9)
        val buffer = ByteBuffer.allocate(bytes.size)
        bytes.forEachIndexed { i, v -> buffer.put(i, v.toByte()) }
        val out = IntArray(2)

        RgbaToArgb().convert(Plane(buffer, rowStride = 8, pixelStride = 4), width = 1, height = 2, out)

        assertEquals(listOf(0xFF010203.toInt(), 0xFF070809.toInt()), out.toList())
    }

    @Test
    fun `target equal to source is the identity`() {
        val plane = grid(3, 2)
        val full = IntArray(6)
        val same = IntArray(6)

        RgbaToArgb().convert(plane, 3, 2, full)
        RgbaToArgb().convert(plane, 3, 2, same, outWidth = 3, outHeight = 2)

        assertEquals(full.toList(), same.toList())
        assertEquals(List(6) { 0xFF000000.toInt() or (it shl 16) }, full.toList())
    }

    @Test
    fun `downscale samples the pixel at the centre of each cell`() {
        val out = IntArray(4)

        RgbaToArgb().convert(grid(4, 4), 4, 4, out, outWidth = 2, outHeight = 2)

        // Source columns/rows 1 and 3; red channel is the pixel index.
        assertEquals(listOf(5, 7, 13, 15), out.map { it shr 16 and 0xFF })
    }

    @Test
    fun `strides are honoured while downscaling`() {
        // 3x2, pixelStride 8 (4 padding bytes per pixel), rowStride 28 (4 padding bytes per row).
        val buffer = ByteBuffer.allocate(56)
        for (row in 0 until 2) for (column in 0 until 3) buffer.put(row * 28 + column * 8, (row * 3 + column).toByte())
        val out = IntArray(2)

        RgbaToArgb().convert(Plane(buffer, rowStride = 28, pixelStride = 8), 3, 2, out, outWidth = 2, outHeight = 1)

        // Columns (1*3/4, 3*3/4) = 0, 2; row (1*2/2) = 1.
        assertEquals(listOf(3, 5), out.map { it shr 16 and 0xFF })
    }

    private fun grid(
        width: Int,
        height: Int,
    ): Plane {
        val buffer = ByteBuffer.allocate(width * height * 4)
        for (i in 0 until width * height) buffer.put(i * 4, i.toByte())
        return Plane(buffer, rowStride = width * 4, pixelStride = 4)
    }
}
