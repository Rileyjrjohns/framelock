package dev.framelock.app

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * TFLite metadata appends a zip archive (holding the label file) to the model's bytes. Android's `ZipFile` refuses data
 * before the archive, so the archive start is computed from its end-of-central-directory record, and the label entry
 * is read from there.
 */
internal object EmbeddedLabels {
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
    private const val END_RECORD_SIZE = 22
    private const val MAX_COMMENT = 0xFFFF
    private const val CENTRAL_DIRECTORY_SIZE_OFFSET = 12
    private const val CENTRAL_DIRECTORY_START_OFFSET = 16

    /** The non-blank lines of the first `.txt` entry, or an empty list when the model has none. */
    fun read(model: File): List<String> {
        val bytes = model.readBytes()
        val start = archiveStart(bytes) ?: return emptyList()
        ZipInputStream(ByteArrayInputStream(bytes, start, bytes.size - start)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return emptyList()
                if (entry.name.endsWith(".txt")) return zip.bufferedReader().readLines().filter { it.isNotBlank() }
            }
        }
    }

    private fun archiveStart(bytes: ByteArray): Int? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val lowest = maxOf(0, bytes.size - END_RECORD_SIZE - MAX_COMMENT)
        for (record in bytes.size - END_RECORD_SIZE downTo lowest) {
            if (buffer.getInt(record) == END_OF_CENTRAL_DIRECTORY) {
                val directorySize = buffer.getInt(record + CENTRAL_DIRECTORY_SIZE_OFFSET)
                val directoryOffset = buffer.getInt(record + CENTRAL_DIRECTORY_START_OFFSET)
                // Offsets in the record are relative to the archive start, which follows the model bytes.
                return (record - directorySize - directoryOffset).takeIf { it in bytes.indices }
            }
        }
        return null
    }
}
