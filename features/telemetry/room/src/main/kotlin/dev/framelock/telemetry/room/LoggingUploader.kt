package dev.framelock.telemetry.room

import dev.framelock.telemetry.domain.TelemetryBatch
import dev.framelock.telemetry.domain.port.TelemetryUploader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * The default uploader (ADR-004): sends nothing, logs what it would send: event count, raw and gzipped sizes and,
 * when [firstEvents] is above zero (debug builds), the first events. An `HttpUploader` behind a configuration flag is
 * future work.
 */
class LoggingUploader(
    private val log: (String) -> Unit,
    private val firstEvents: Int = 0,
) : TelemetryUploader {
    override suspend fun upload(batch: TelemetryBatch): Boolean {
        val gzipped = batch.gzippedJson.size
        log("Upload of ${batch.eventCount} events: $gzipped B gzipped, ${batch.rawSizeBytes} B raw")
        if (firstEvents > 0) {
            Json
                .parseToJsonElement(TelemetryBatchBuilder.json(batch))
                .jsonArray
                .take(firstEvents)
                .forEach { log("  $it") }
        }
        return true
    }
}
