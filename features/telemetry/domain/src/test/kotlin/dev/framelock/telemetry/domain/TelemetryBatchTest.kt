package dev.framelock.telemetry.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TelemetryBatchTest {
    @Test
    fun `a batch keeps its count, raw size and compressed bytes`() {
        val batch = TelemetryBatch(eventCount = 2, rawSizeBytes = 40, gzippedJson = byteArrayOf(1, 2, 3))

        assertEquals(2, batch.eventCount)
        assertEquals(40, batch.rawSizeBytes)
        assertEquals(3, batch.gzippedJson.size)
    }

    @Test
    fun `an empty batch is refused`() {
        assertThrows(IllegalArgumentException::class.java) { TelemetryBatch(0, 2, byteArrayOf(1)) }
    }

    @Test
    fun `a batch without bytes is refused`() {
        assertThrows(IllegalArgumentException::class.java) { TelemetryBatch(1, 0, byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { TelemetryBatch(1, 2, byteArrayOf()) }
    }
}
