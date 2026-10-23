package dev.framelock.telemetry.room

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggingUploaderTest {
    private val logs = mutableListOf<String>()
    private val batch =
        TelemetryBatchBuilder.build(
            listOf(
                """{"type":"AppSession","atMillis":1}""",
                """{"type":"AppSession","atMillis":2}""",
                """{"type":"AppSession","atMillis":3}""",
            ),
        )

    @Test
    fun `it accepts the batch and logs the count and both sizes`() =
        runTest {
            val accepted = LoggingUploader({ logs += it }).upload(batch)

            assertTrue(accepted)
            assertEquals(1, logs.size)
            assertTrue(
                "3 events" in logs[0] && "${batch.gzippedJson.size} B gzipped" in logs[0] &&
                    "${batch.rawSizeBytes} B raw" in logs[0],
            )
        }

    @Test
    fun `it logs the first events when asked to`() =
        runTest {
            LoggingUploader({ logs += it }, firstEvents = 2).upload(batch)

            assertEquals(3, logs.size)
            assertTrue("atMillis\":1" in logs[1] && "atMillis\":2" in logs[2])
        }
}
