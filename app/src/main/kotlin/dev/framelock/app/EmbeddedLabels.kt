package dev.framelock.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * TFLite metadata appends a zip archive (holding the label file) to the model's bytes. Android's `ZipFile` refuses data
 * before the archive, so the archive is read from its central directory. Publishers disagree on what its offsets are
 * relative to: the EfficientDet-Lite0 build counts them from the start of the model file, a plain appended zip from
 * the start of the archive. The shift between the two is measured once, from where the directory actually lies.
 */
internal object EmbeddedLabels {
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
    private const val CENTRAL_ENTRY = 0x02014b50
    private const val LOCAL_ENTRY = 0x04034b50
    private const val END_RECORD_SIZE = 22
    private const val MAX_COMMENT = 0xFFFF
    private const val STORED = 0
    private const val DEFLATED = 8

    /** The non-blank lines of the first `.txt` entry, or an empty list when the model has none. */
    fun read(model: File): List<String> {
        val bytes = model.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val record = endRecord(buffer, bytes.size) ?: return emptyList()
        val entries = buffer.getShort(record + 10).toInt() and 0xFFFF
        val directorySize = buffer.getInt(record + 12)
        val directory = record - directorySize
        val shift = directory - buffer.getInt(record + 16)
        var entry = directory
        repeat(entries) {
            check(buffer.getInt(entry) == CENTRAL_ENTRY) { "Corrupt zip directory at $entry" }
            val method = buffer.getShort(entry + 10).toInt()
            val compressedSize = buffer.getInt(entry + 20)
            val size = buffer.getInt(entry + 24)
            val nameLength = buffer.getShort(entry + 28).toInt() and 0xFFFF
            val extraLength = buffer.getShort(entry + 30).toInt() and 0xFFFF
            val commentLength = buffer.getShort(entry + 32).toInt() and 0xFFFF
            val local = shift + buffer.getInt(entry + 42)
            val name = String(bytes, entry + 46, nameLength, Charsets.UTF_8)
            if (name.endsWith(".txt")) {
                check(buffer.getInt(local) == LOCAL_ENTRY) { "Corrupt zip entry $name at $local" }
                val data =
                    local + 30 + (buffer.getShort(local + 26).toInt() and 0xFFFF) +
                        (buffer.getShort(local + 28).toInt() and 0xFFFF)
                return content(bytes, data, compressedSize, size, method).lines().filter { it.isNotBlank() }
            }
            entry += 46 + nameLength + extraLength + commentLength
        }
        return emptyList()
    }

    private fun endRecord(
        buffer: ByteBuffer,
        size: Int,
    ): Int? {
        val lowest = maxOf(0, size - END_RECORD_SIZE - MAX_COMMENT)
        return (size - END_RECORD_SIZE downTo lowest).firstOrNull { buffer.getInt(it) == END_OF_CENTRAL_DIRECTORY }
    }

    private fun content(
        bytes: ByteArray,
        offset: Int,
        compressedSize: Int,
        size: Int,
        method: Int,
    ): String =
        when (method) {
            STORED -> {
                String(bytes, offset, compressedSize, Charsets.UTF_8)
            }

            DEFLATED -> {
                val inflater = Inflater(true)
                try {
                    inflater.setInput(bytes, offset, compressedSize)
                    val out = ByteArray(size)
                    var read = 0
                    while (read < size && !inflater.finished()) read += inflater.inflate(out, read, size - read)
                    String(out, 0, read, Charsets.UTF_8)
                } finally {
                    inflater.end()
                }
            }

            else -> {
                error("Unsupported zip compression method $method")
            }
        }
}
