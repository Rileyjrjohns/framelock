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

        I420ToArgb().convert(planes(2, 2, y = 128, u = 128, v = 128), width = 2, height = 2, out)

        assertEquals(List(4) { 0xFF808080.toInt() }, out.toList())
    }

    @Test
    fun `strong red chroma gives a red pixel`() {
        val out = IntArray(4)

        I420ToArgb().convert(planes(2, 2, y = 82, u = 90, v = 240), width = 2, height = 2, out)

        val pixel = out[0]
        val (r, g, b) = Triple(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)
        assertTrue(r > 200 && g < 60 && b < 60, "rgb=$r,$g,$b")
        assertEquals(0xFF, pixel ushr 24)
    }

    @Test
    fun `target equal to source is the identity of the full conversion`() {
        val planes = gradient(4, 4)
        val full = IntArray(16)
        val same = IntArray(16)

        I420ToArgb().convert(planes, 4, 4, full)
        I420ToArgb().convert(planes, 4, 4, same, outWidth = 4, outHeight = 4)

        assertEquals(full.toList(), same.toList())
    }

    @Test
    fun `downscale by two samples the pixel at the centre of each cell`() {
        // Luma = 10 * (row * 4 + column), neutral chroma, so the pixel value is the sampled luma.
        val out = IntArray(4)

        I420ToArgb().convert(gradient(4, 4), 4, 4, out, outWidth = 2, outHeight = 2)

        // Cell centres map to source columns/rows 1 and 3.
        assertEquals(listOf(50, 70, 130, 150), out.map { it and 0xFF })
    }

    @Test
    fun `chroma follows the sampled pixel`() {
        // 4x2 frame: left half red chroma, right half blue chroma; downscaled to 2x1 each side keeps its colour.
        val y = ByteBuffer.wrap(ByteArray(8) { 128.toByte() })
        val u = ByteBuffer.wrap(byteArrayOf(90.toByte(), 240.toByte()))
        val v = ByteBuffer.wrap(byteArrayOf(240.toByte(), 90.toByte()))
        val out = IntArray(2)

        I420ToArgb().convert(
            listOf(Plane(y, 4, 1), Plane(u, 2, 1), Plane(v, 2, 1)),
            4,
            2,
            out,
            outWidth = 2,
            outHeight = 1,
        )

        assertTrue((out[0] shr 16 and 0xFF) > (out[0] and 0xFF), "left should be red")
        assertTrue((out[1] and 0xFF) > (out[1] shr 16 and 0xFF), "right should be blue")
    }

    @Test
    fun `row and pixel strides are honoured`() {
        // 2x2 frame, luma rowStride 4 (2 padding bytes), chroma interleaved-style pixelStride 2, rowStride 4.
        val y = ByteBuffer.wrap(byteArrayOf(10, 20, 99, 99, 30, 40, 99, 99))
        val u = ByteBuffer.wrap(byteArrayOf(128.toByte(), 0, 0, 0))
        val v = ByteBuffer.wrap(byteArrayOf(128.toByte(), 0, 0, 0))
        val out = IntArray(4)

        I420ToArgb().convert(listOf(Plane(y, 4, 1), Plane(u, 4, 2), Plane(v, 4, 2)), 2, 2, out)

        assertEquals(listOf(10, 20, 30, 40), out.map { it and 0xFF })
    }

    @Test
    fun `odd sizes convert and downscale without leaving the frame`() {
        val width = 5
        val height = 3
        val y = ByteBuffer.wrap(ByteArray(width * height) { (it * 10).toByte() })
        val chroma = { ByteBuffer.wrap(ByteArray(3 * 2) { 128.toByte() }) }
        val planes = listOf(Plane(y, width, 1), Plane(chroma(), 3, 1), Plane(chroma(), 3, 1))
        val full = IntArray(width * height)
        val small = IntArray(2 * 2)
        val converter = I420ToArgb()

        converter.convert(planes, width, height, full)
        converter.convert(planes, width, height, small, outWidth = 2, outHeight = 2)

        assertEquals(List(width * height) { it * 10 }, full.map { it and 0xFF })
        // Centres: columns (1*5/4, 3*5/4) = 1, 3; rows (1*3/4, 3*3/4) = 0, 2.
        assertEquals(listOf(10, 30, 110, 130), small.map { it and 0xFF })
    }

    @Test
    fun `one converter handles a size change and the source buffer position is left alone`() {
        val planes = gradient(4, 4)
        val converter = I420ToArgb()
        val big = IntArray(16)
        val small = IntArray(4)
        val position = planes[0].buffer.position()

        converter.convert(planes, 4, 4, big)
        converter.convert(planes, 4, 4, small, outWidth = 2, outHeight = 2)
        converter.convert(planes, 4, 4, big)

        assertEquals(List(16) { it * 10 }, big.map { it and 0xFF })
        assertEquals(listOf(50, 70, 130, 150), small.map { it and 0xFF })
        assertEquals(position, planes[0].buffer.position())
    }

    private fun gradient(
        width: Int,
        height: Int,
    ) = listOf(
        Plane(ByteBuffer.wrap(ByteArray(width * height) { (it * 10).toByte() }), width, 1),
        Plane(filled(width * height / 4, 128), width / 2, 1),
        Plane(filled(width * height / 4, 128), width / 2, 1),
    )

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
