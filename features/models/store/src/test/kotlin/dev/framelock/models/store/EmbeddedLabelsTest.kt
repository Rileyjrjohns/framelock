package dev.framelock.models.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmbeddedLabelsTest {
    @TempDir
    lateinit var dir: File

    private val prefix = ByteArray(4_096) { (it % 251).toByte() }
    private val labels = "person\nbicycle\n\ncar\n"

    @Test
    fun `reads a zip whose offsets count from the archive start`() {
        val model = File(dir, "relative.tflite")
        model.writeBytes(prefix + zip(ZipEntry.DEFLATED))

        assertEquals(listOf("person", "bicycle", "car"), EmbeddedLabels.read(model))
    }

    @Test
    fun `reads a zip whose offsets count from the model start, as the EfficientDet-Lite0 metadata does`() {
        val model = File(dir, "absolute.tflite")
        model.writeBytes(shiftOffsets(prefix + zip(ZipEntry.DEFLATED), prefix.size))

        assertEquals(listOf("person", "bicycle", "car"), EmbeddedLabels.read(model))
    }

    @Test
    fun `reads a stored entry`() {
        val model = File(dir, "stored.tflite")
        model.writeBytes(shiftOffsets(prefix + zip(ZipEntry.STORED), prefix.size))

        assertEquals(listOf("person", "bicycle", "car"), EmbeddedLabels.read(model))
    }

    @Test
    fun `reads the labels of the development model when it has been fetched`() {
        val model = File("../../live/inference-litert/src/androidTest/assets/efficientdet_lite0.tflite")
        assumeTrue(model.exists(), "run scripts/fetch-model.sh first")

        val read = EmbeddedLabels.read(model)

        assertEquals("person", read.first())
        assertEquals(DEVELOPMENT_MODEL_LABELS, read.size)
    }

    @Test
    fun `a model without metadata has no labels`() {
        val model = File(dir, "plain.tflite")
        model.writeBytes(ByteArray(1_024) { 7 })

        assertEquals(emptyList<String>(), EmbeddedLabels.read(model))
    }

    private fun zip(method: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val content = labels.toByteArray()
        ZipOutputStream(bytes).use { out ->
            out.putNextEntry(
                ZipEntry("labelmap.txt").apply {
                    this.method = method
                    if (method == ZipEntry.STORED) {
                        size = content.size.toLong()
                        crc = CRC32().apply { update(content) }.value
                    }
                },
            )
            out.write(content)
            out.closeEntry()
        }
        return bytes.toByteArray()
    }

    /** Rewrites the directory and entry offsets of the zip ending [bytes] so they count from the file start. */
    private fun shiftOffsets(
        bytes: ByteArray,
        by: Int,
    ): ByteArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val record = (bytes.size - 22 downTo 0).first { buffer.getInt(it) == 0x06054b50 }
        val directory = buffer.getInt(record + 16) + by
        buffer.putInt(record + 16, directory)
        var entry = directory
        repeat(buffer.getShort(record + 10).toInt()) {
            buffer.putInt(entry + 42, buffer.getInt(entry + 42) + by)
            entry += 46 + buffer.getShort(entry + 28) + buffer.getShort(entry + 30) + buffer.getShort(entry + 32)
        }
        return bytes
    }

    private companion object {
        const val DEVELOPMENT_MODEL_LABELS = 90
    }
}
