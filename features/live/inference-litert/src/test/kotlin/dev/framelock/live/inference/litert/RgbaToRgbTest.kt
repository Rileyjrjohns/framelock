package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RgbaToRgbTest {
    @Test
    fun `same size copies the colour channels and drops the fourth byte`() {
        val plane = plane(width = 2, height = 2, rowStride = 8) { x, y -> intArrayOf(x * 10, y * 10, 5, 77) }

        val rgb = convert(plane, 2, 2, 2, 2)

        assertEquals(listOf(0, 0, 5, 10, 0, 5, 0, 10, 5, 10, 10, 5), rgb)
    }

    @Test
    fun `downscale picks the nearest source pixels`() {
        val plane = plane(width = 4, height = 4, rowStride = 16) { x, y -> intArrayOf(x, y, 0, 255) }

        val rgb = convert(plane, 4, 4, 2, 2)

        assertEquals(listOf(0, 0, 0, 2, 0, 0, 0, 2, 0, 2, 2, 0), rgb)
    }

    @Test
    fun `row stride padding is skipped`() {
        val plane = plane(width = 2, height = 2, rowStride = 16) { x, y -> intArrayOf(x + 1, y + 1, 9, 0) }

        val rgb = convert(plane, 2, 2, 2, 2)

        assertEquals(listOf(1, 1, 9, 2, 1, 9, 1, 2, 9, 2, 2, 9), rgb)
    }

    @Test
    fun `alpha value does not change the output`() {
        val opaque = plane(2, 2, 8) { _, _ -> intArrayOf(10, 20, 30, 255) }
        val clear = plane(2, 2, 8) { _, _ -> intArrayOf(10, 20, 30, 0) }

        assertEquals(convert(opaque, 2, 2, 2, 2), convert(clear, 2, 2, 2, 2))
    }

    private fun convert(
        plane: Plane,
        width: Int,
        height: Int,
        outWidth: Int,
        outHeight: Int,
    ): List<Int> {
        val out = ByteBuffer.allocateDirect(outWidth * outHeight * 3)
        RgbaToRgb.scaleInto(plane, width, height, out, outWidth, outHeight)
        assertEquals(out.capacity(), out.position())
        out.flip()
        return List(out.remaining()) { out.get().toInt() and 0xFF }
    }

    private fun plane(
        width: Int,
        height: Int,
        rowStride: Int,
        pixel: (Int, Int) -> IntArray,
    ): Plane {
        val buffer = ByteBuffer.allocateDirect(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixel(x, y).forEachIndexed { i, v -> buffer.put(y * rowStride + x * 4 + i, v.toByte()) }
            }
        }
        return Plane(buffer, rowStride = rowStride, pixelStride = 4)
    }
}
