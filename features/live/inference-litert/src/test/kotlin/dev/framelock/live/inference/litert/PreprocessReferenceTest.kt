package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.Random

/**
 * The table-driven converters must feed the model exactly the bytes the straightforward per-pixel algorithm did: the
 * references below are that algorithm, kept for the comparison only.
 */
class PreprocessReferenceTest {
    @Test
    fun `yuv matches the reference on random frames with padded rows and interleaved chroma`() {
        val random = Random(SEED)
        // (width, height, outWidth, outHeight, lumaPad, chromaPixelStride, chromaPad)
        val cases =
            listOf(
                intArrayOf(64, 48, 32, 24, 0, 1, 0),
                intArrayOf(67, 51, 32, 32, 5, 1, 3),
                intArrayOf(33, 17, 20, 9, 7, 2, 1),
                intArrayOf(1920, 1080, 320, 320, 0, 1, 0),
                intArrayOf(1920, 1080, 320, 320, 64, 2, 32),
                intArrayOf(31, 29, 31, 29, 3, 2, 5),
                intArrayOf(16, 16, 40, 33, 1, 1, 1),
            )
        val converter = YuvConverter()
        for (case in cases) {
            val (width, height, outWidth, outHeight, lumaPad, chromaPixelStride, chromaPad) = case
            val chromaWidth = (width + 1) / 2
            val chromaHeight = (height + 1) / 2
            val planes =
                listOf(
                    randomPlane(random, width + lumaPad, height, 1, width),
                    randomPlane(
                        random,
                        chromaWidth * chromaPixelStride + chromaPad,
                        chromaHeight,
                        chromaPixelStride,
                        chromaWidth,
                    ),
                    randomPlane(
                        random,
                        chromaWidth * chromaPixelStride + chromaPad,
                        chromaHeight,
                        chromaPixelStride,
                        chromaWidth,
                    ),
                )
            val expected = ByteBuffer.allocate(outWidth * outHeight * 3)
            referenceYuv(planes, width, height, expected, outWidth, outHeight)
            // Twice on the same instance: the second call runs on cached tables.
            repeat(2) {
                val actual = ByteBuffer.allocateDirect(outWidth * outHeight * 3)
                converter.scaleInto(planes, width, height, actual, outWidth, outHeight)
                assertEquals(actual.capacity(), actual.position())
                assertArrayEquals(expected.array(), bytes(actual), "case=${case.toList()}")
            }
        }
    }

    @Test
    fun `yuv leaves the source buffers untouched`() {
        val planes =
            listOf(
                randomPlane(Random(SEED), 20, 10, 1, 16),
                randomPlane(Random(SEED), 10, 5, 1, 8),
                randomPlane(Random(SEED), 10, 5, 1, 8),
            )
        YuvToRgb.scaleInto(planes, 16, 10, ByteBuffer.allocateDirect(8 * 5 * 3), 8, 5)

        planes.forEach { assertEquals(0, it.buffer.position()) }
    }

    @Test
    fun `rgba matches the reference on random frames with padded rows`() {
        val random = Random(SEED)
        // (width, height, outWidth, outHeight, rowPad, pixelStride)
        val cases =
            listOf(
                intArrayOf(64, 48, 32, 24, 0, 4),
                intArrayOf(67, 51, 32, 32, 5, 4),
                intArrayOf(33, 17, 20, 9, 7, 4),
                intArrayOf(1920, 1080, 320, 320, 0, 4),
                intArrayOf(31, 29, 31, 29, 3, 5),
                intArrayOf(16, 16, 40, 33, 1, 4),
            )
        val converter = RgbaConverter()
        for (case in cases) {
            val (width, height, outWidth, outHeight, rowPad, pixelStride) = case
            val plane = randomPlane(random, width * pixelStride + rowPad, height, pixelStride, width, pixelBytes = 4)
            val expected = ByteBuffer.allocate(outWidth * outHeight * 3)
            referenceRgba(plane, width, height, expected, outWidth, outHeight)
            repeat(2) {
                val actual = ByteBuffer.allocateDirect(outWidth * outHeight * 3)
                converter.scaleInto(plane, width, height, actual, outWidth, outHeight)
                assertEquals(actual.capacity(), actual.position())
                assertArrayEquals(expected.array(), bytes(actual), "case=${case.toList()}")
            }
        }
    }

    private operator fun IntArray.component6() = this[5]

    private operator fun IntArray.component7() = this[6]

    /** Random bytes everywhere, padding included; the buffer is exactly as long as its last sampled byte needs. */
    private fun randomPlane(
        random: Random,
        rowStride: Int,
        rows: Int,
        pixelStride: Int,
        columns: Int,
        pixelBytes: Int = 1,
    ): Plane {
        val array = ByteArray((rows - 1) * rowStride + (columns - 1) * pixelStride + pixelBytes)
        random.nextBytes(array)
        return Plane(ByteBuffer.allocateDirect(array.size).put(array).apply { rewind() }, rowStride, pixelStride)
    }

    private fun bytes(buffer: ByteBuffer): ByteArray =
        ByteArray(buffer.capacity()).also {
            buffer.duplicate().apply { rewind() }.get(it)
        }

    private fun referenceYuv(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) {
        val (y, u, v) = planes
        for (row in 0 until outHeight) {
            val sourceRow = row * height / outHeight
            for (column in 0 until outWidth) {
                val sourceColumn = column * width / outWidth
                val luma = y.byteAt(sourceRow, sourceColumn)
                val cb = u.byteAt(sourceRow / 2, sourceColumn / 2) - 128
                val cr = v.byteAt(sourceRow / 2, sourceColumn / 2) - 128
                out.put(clamp(luma + (1436 * cr shr 10)))
                out.put(clamp(luma - ((352 * cb + 731 * cr) shr 10)))
                out.put(clamp(luma + (1815 * cb shr 10)))
            }
        }
    }

    private fun referenceRgba(
        plane: Plane,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) {
        for (row in 0 until outHeight) {
            val rowStart = (row * height / outHeight) * plane.rowStride
            for (column in 0 until outWidth) {
                val offset = rowStart + (column * width / outWidth) * plane.pixelStride
                out.put(plane.buffer.get(offset))
                out.put(plane.buffer.get(offset + 1))
                out.put(plane.buffer.get(offset + 2))
            }
        }
    }

    private fun Plane.byteAt(
        row: Int,
        column: Int,
    ): Int = buffer.get(row * rowStride + column * pixelStride).toInt() and 255

    private fun clamp(value: Int): Byte = value.coerceIn(0, 255).toByte()

    private companion object {
        const val SEED = 20260101L
    }
}
