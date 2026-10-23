package dev.framelock.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmbeddedLabelsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `reads the label file of a zip appended to model bytes`() {
        val model = File(dir, "model.tflite")
        model.writeBytes(ByteArray(4_096) { (it % 251).toByte() } + zip("labelmap.txt" to "person\nbicycle\n\ncar\n"))

        assertEquals(listOf("person", "bicycle", "car"), EmbeddedLabels.read(model))
    }

    @Test
    fun `a model without metadata has no labels`() {
        val model = File(dir, "plain.tflite")
        model.writeBytes(ByteArray(1_024) { 7 })

        assertEquals(emptyList<String>(), EmbeddedLabels.read(model))
    }

    private fun zip(entry: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            out.putNextEntry(ZipEntry(entry.first))
            out.write(entry.second.toByteArray())
            out.closeEntry()
        }
        return bytes.toByteArray()
    }
}
