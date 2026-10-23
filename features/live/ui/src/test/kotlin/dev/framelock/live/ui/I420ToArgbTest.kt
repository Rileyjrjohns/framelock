package dev.framelock.live.ui

import dev.framelock.live.domain.frame.Plane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class I420ToArgbTest {
    @Test
    fun `mid grey gives opaque mid grey`() {
        val out = IntArray(4)

        I420ToArgb.convert(planes(2, 2, y = 128, u = 128, v = 128), width = 2, height = 2, out)

        assertEquals(List(4) { 0xFF808080.toInt() }, out.toList())
    }

    @Test
    fun `strong red chroma gives a red pixel`() {
        val out = IntArray(4)

        I420ToArgb.convert(planes(2, 2, y = 82, u = 90, v = 240), width = 2, height = 2, out)

        val pixel = out[0]
        val (r, g, b) = Triple(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)
        assertTrue(r > 200 && g < 60 && b < 60, "rgb=$r,$g,$b")
        assertEquals(0xFF, pixel ushr 24)
    }

    private fun planes(
        width: Int,
        height: Int,
        y: Int,
        u: Int,
        v: Int,
    ) = listOf(
        Plane(filled(width * height, y), width, 1),
        Plane(filled(width * height / 4, u), width / 2, 1),
        Plane(filled(width * height / 4, v), width / 2, 1),
    )

    private fun filled(
        size: Int,
        value: Int,
    ) = ByteBuffer.allocate(size).apply {
        repeat(size) { put(value.toByte()) }
        flip()
    }
}
