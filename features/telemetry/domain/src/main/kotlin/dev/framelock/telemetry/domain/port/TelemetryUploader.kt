package dev.framelock.telemetry.domain.port

import dev.framelock.telemetry.domain.TelemetryBatch

/** Where buffered batches go (M6). Implemented in the adapter: it logs by default, an HTTP endpoint is future work. */
interface TelemetryUploader {
    /** Returns true when the whole batch was accepted, so its events may be deleted; false keeps them for a retry. */
    suspend fun upload(batch: TelemetryBatch): Boolean
}
