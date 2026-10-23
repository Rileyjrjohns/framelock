package dev.framelock.telemetry.room

import dev.framelock.telemetry.domain.TelemetryBatch
import dev.framelock.telemetry.domain.port.TelemetryUploader
import kotlinx.coroutines.CancellationException

/**
 * One upload run (ADR-004, M6): reads the oldest events in pages of [batchSize], uploads each page as one gzipped JSON
 * array and deletes the page only when the uploader accepted it. Stops when the buffer is empty, after [maxBatches]
 * pages (the next run continues), or at the first failure, which keeps its events.
 */
class TelemetryUploadRun(
    private val buffer: TelemetryBuffer,
    private val uploader: TelemetryUploader,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxBatches: Int = DEFAULT_MAX_BATCHES,
    private val log: (String) -> Unit = {},
) {
    enum class Outcome {
        /** The buffer is empty. */
        DRAINED,

        /** [maxBatches] pages went out and events remain. */
        LIMIT_REACHED,

        /** An upload failed; its events are still buffered. */
        FAILED,
    }

    init {
        require(batchSize > 0 && maxBatches > 0) { "Batch size and batch limit must be positive" }
    }

    suspend fun run(): Outcome {
        repeat(maxBatches) {
            val page = buffer.oldest(batchSize)
            if (page.isEmpty()) return Outcome.DRAINED
            val batch = TelemetryBatchBuilder.build(page.map { it.payload })
            if (!accepted(batch)) return Outcome.FAILED
            buffer.delete(page.map { it.id })
        }
        return Outcome.LIMIT_REACHED
    }

    private suspend fun accepted(batch: TelemetryBatch): Boolean =
        try {
            uploader.upload(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log("Telemetry upload threw ${e::class.simpleName}; ${batch.eventCount} events kept")
            false
        }

    companion object {
        const val DEFAULT_BATCH_SIZE = 500
        const val DEFAULT_MAX_BATCHES = 20
    }
}
