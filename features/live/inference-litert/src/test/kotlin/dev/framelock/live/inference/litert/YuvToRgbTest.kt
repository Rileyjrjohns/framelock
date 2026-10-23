package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class YuvToRgbTest {
    @Test
    fun `mid grey stays mid grey`() {
        val rgb = convert(width = 4, height = 4, y = 128, u = 128, v = 128, outWidth = 2, outHeight = 2)

        assertEquals(List(12) { 128 }, rgb)
    }

    @Test
    fun `strong red chroma gives a red pixel`() {
        val rgb = convert(width = 2, height = 2, y = 82, u = 90, v = 240, outWidth = 1, outHeight = 1)

        val (r, g, b) = rgb
        assertEquals(true, r > 200 && g < 60 && b < 60, "rgb=$rgb")
    }

    @Test
    fun `output is scaled to the requested size`() {
        val out = ByteBuffer.allocateDirect(3 * 3 * 3)

        YuvToRgb.scaleInto(image(6, 6, 16, 128, 128), 6, 6, out, 3, 3)

        assertEquals(27, out.position())
    }

    private fun convert(
        width: Int,
        height: Int,
        y: Int,
        u: Int,
        v: Int,
        outWidth: Int,
        outHeight: Int,
    ): List<Int> {
        val out = ByteBuffer.allocateDirect(outWidth * outHeight * 3)
        YuvToRgb.scaleInto(image(width, height, y, u, v), width, height, out, outWidth, outHeight)
        out.flip()
        return List(out.remaining()) { out.get().toInt() and 0xFF }
    }

    private fun image(
        width: Int,
        height: Int,
        y: Int,
        u: Int,
        v: Int,
    ): List<Plane> =
        listOf(
            Plane(filled(width * height, y), rowStride = width, pixelStride = 1),
            Plane(filled(width * height / 4, u), rowStride = width / 2, pixelStride = 1),
            Plane(filled(width * height / 4, v), rowStride = width / 2, pixelStride = 1),
        )

    private fun filled(
        size: Int,
        value: Int,
    ) = ByteBuffer.allocateDirect(size).apply {
        repeat(size) { put(value.toByte()) }
        flip()
    }
}
