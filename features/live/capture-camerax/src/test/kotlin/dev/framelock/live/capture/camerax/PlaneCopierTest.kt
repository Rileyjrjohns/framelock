package dev.framelock.live.capture.camerax

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.Random

class PlaneCopierTest {
    private val copier = PlaneCopier()
    private val random = Random(42)

    @Test
    fun `matches the per-byte reference for random planes with padding and both pixel strides`() {
        for (pixelStride in 1..3) {
            for (padding in listOf(0, 1, 5)) {
                for ((columns, rows) in listOf(1 to 1, 4 to 3, 16 to 9, 33 to 7)) {
                    val rowStride = (columns - 1) * pixelStride + 1 + padding
                    val size = (rows - 1) * rowStride + (columns - 1) * pixelStride + 1
                    val data = ByteArray(size + 3).also { random.nextBytes(it) }
                    val source = SourcePlane(direct(data), rowStride, pixelStride)
                    source.buffer.position(3)
                    val target = ByteBuffer.allocateDirect(columns * rows)

                    copier.copy(source, target, columns, rows)

                    assertArrayEquals(
                        reference(source, columns, rows),
                        bytes(target),
                        "stride $pixelStride pad $padding",
                    )
                    assertEquals(columns * rows, target.remaining())
                }
            }
        }
    }

    @Test
    fun `tight planes with a pixel stride of 1 are copied whole`() {
        val data = ByteArray(24).also { random.nextBytes(it) }
        val target = ByteBuffer.allocateDirect(24)

        copier.copy(SourcePlane(direct(data), rowStride = 6, pixelStride = 1), target, columns = 6, rows = 4)

        assertArrayEquals(data, bytes(target))
    }

    @Test
    fun `the source position and limit are left alone`() {
        val source = SourcePlane(direct(ByteArray(40).also { random.nextBytes(it) }), rowStride = 8, pixelStride = 2)
        source.buffer.position(2)
        source.buffer.limit(38)

        copier.copy(source, ByteBuffer.allocateDirect(12), columns = 4, rows = 3)
        copier.copy(SourcePlane(source.buffer, 8, 1), ByteBuffer.allocateDirect(12), columns = 4, rows = 3)

        assertEquals(2, source.buffer.position())
        assertEquals(38, source.buffer.limit())
    }

    @Test
    fun `reads stop at the last byte of the plane`() {
        // Interleaved plane of 3 columns x 2 rows ends right after its last sample: no trailing byte to read.
        val data = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6)
        val target = ByteBuffer.allocateDirect(6)

        copier.copy(SourcePlane(direct(data), rowStride = 6, pixelStride = 2), target, columns = 3, rows = 2)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), bytes(target))
    }

    @Test
    fun `repeated copies reuse the views and the scratch arrays`() {
        val a = direct(ByteArray(64).also { random.nextBytes(it) })
        val b = direct(ByteArray(64).also { random.nextBytes(it) })
        val target = ByteBuffer.allocateDirect(12)
        copier.copy(SourcePlane(a, 8, 2), target, 4, 3)
        copier.copy(SourcePlane(b, 8, 2), target, 4, 3)
        val views = copier.cachedViews
        val scratch = copier.scratchBytes

        repeat(50) {
            copier.copy(SourcePlane(a, 8, 2), target, 4, 3)
            copier.copy(SourcePlane(b, 9, 1), target, 4, 3)
        }

        assertEquals(2, views)
        assertEquals(views, copier.cachedViews)
        assertEquals(scratch, copier.scratchBytes)
        assertTrue(scratch > 0)
    }

    /** The previous algorithm: absolute reads, one byte at a time. */
    private fun reference(
        source: SourcePlane,
        columns: Int,
        rows: Int,
    ): ByteArray {
        val out = ByteArray(columns * rows)
        val start = source.buffer.position()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                out[row * columns + column] =
                    source.buffer.get(start + row * source.rowStride + column * source.pixelStride)
            }
        }
        return out
    }

    private fun direct(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.rewind() }

    private fun bytes(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        copy.rewind()
        return ByteArray(copy.remaining()).also { copy.get(it) }
    }
}
