package dev.framelock.telemetry.room

import dev.framelock.telemetry.domain.TelemetryBatch
import dev.framelock.telemetry.domain.port.TelemetryUploader
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetryUploadRunTest {
    private class RecordingUploader(
        private val results: (Int) -> Boolean = { true },
    ) : TelemetryUploader {
        val counts = mutableListOf<Int>()
        var failure: RuntimeException? = null

        override suspend fun upload(batch: TelemetryBatch): Boolean {
            failure?.let { throw it }
            counts += batch.eventCount
            return results(counts.size)
        }
    }

    private val buffer = InMemoryTelemetryBuffer()

    @Test
    fun `an empty buffer uploads nothing`() =
        runTest {
            val uploader = RecordingUploader()

            val outcome = TelemetryUploadRun(buffer, uploader).run()

            assertEquals(TelemetryUploadRun.Outcome.DRAINED, outcome)
            assertEquals(emptyList<Int>(), uploader.counts)
        }

    @Test
    fun `events go out oldest first in pages and are deleted once accepted`() =
        runTest {
            buffer.seed(1_200)
            val uploader = RecordingUploader()

            val outcome = TelemetryUploadRun(buffer, uploader, batchSize = 500).run()

            assertEquals(TelemetryUploadRun.Outcome.DRAINED, outcome)
            assertEquals(listOf(500, 500, 200), uploader.counts)
            assertEquals(0, buffer.count())
        }

    @Test
    fun `a failed upload keeps its events and later ones`() =
        runTest {
            buffer.seed(30)
            val uploader = RecordingUploader { call -> call < 2 }

            val outcome = TelemetryUploadRun(buffer, uploader, batchSize = 10).run()

            assertEquals(TelemetryUploadRun.Outcome.FAILED, outcome)
            assertEquals(listOf(10, 10), uploader.counts)
            assertEquals((11L..30L).toList(), buffer.stored.map { it.first })
        }

    @Test
    fun `an uploader that throws is a failure and keeps the events`() =
        runTest {
            buffer.seed(5)
            val uploader = RecordingUploader().apply { failure = IllegalStateException("offline") }

            val outcome = TelemetryUploadRun(buffer, uploader).run()

            assertEquals(TelemetryUploadRun.Outcome.FAILED, outcome)
            assertEquals(5, buffer.count())
        }

    @Test
    fun `a run stops at the page limit and leaves the rest for the next run`() =
        runTest {
            buffer.seed(50)
            val uploader = RecordingUploader()

            val outcome = TelemetryUploadRun(buffer, uploader, batchSize = 10, maxBatches = 3).run()

            assertEquals(TelemetryUploadRun.Outcome.LIMIT_REACHED, outcome)
            assertEquals(20, buffer.count())
        }

    @Test
    fun `a run that ends exactly at the page limit with nothing left is still limit reached`() =
        runTest {
            buffer.seed(30)

            val outcome = TelemetryUploadRun(buffer, RecordingUploader(), batchSize = 10, maxBatches = 3).run()

            assertEquals(TelemetryUploadRun.Outcome.LIMIT_REACHED, outcome)
            assertEquals(0, buffer.count())
        }
}
