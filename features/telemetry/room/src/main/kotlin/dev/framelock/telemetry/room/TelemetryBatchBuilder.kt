package dev.framelock.telemetry.room

import dev.framelock.telemetry.domain.TelemetryBatch
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Joins stored events (one JSON object each) into a gzipped JSON array: the body of an upload (ADR-004, M6). */
object TelemetryBatchBuilder {
    /** [payloads] must be non-empty; each is a JSON object produced by [TelemetryEventEncoder]. */
    fun build(payloads: List<String>): TelemetryBatch {
        val raw = payloads.joinToString(separator = ",", prefix = "[", postfix = "]").toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(raw) }
        return TelemetryBatch(
            eventCount = payloads.size,
            rawSizeBytes = raw.size,
            gzippedJson = compressed.toByteArray(),
        )
    }

    /** The JSON array text of [batch]; for logging and tests. */
    fun json(batch: TelemetryBatch): String =
        GZIPInputStream(batch.gzippedJson.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
}
