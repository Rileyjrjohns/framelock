package dev.framelock.telemetry.room

import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.testing.FakeTelemetrySink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TeeTelemetrySinkTest {
    private val first = FakeTelemetrySink()
    private val second = FakeTelemetrySink()

    @Test
    fun `every sink receives the event`() {
        TeeTelemetrySink(first, second).record(UsageTelemetry.AppSession)

        assertEquals(listOf<Any>(UsageTelemetry.AppSession), first.events)
        assertEquals(listOf<Any>(UsageTelemetry.AppSession), second.events)
    }

    @Test
    fun `a failing sink does not stop the next one and its failure is rethrown`() {
        first.failWith(IllegalStateException("boom"))

        assertThrows(
            IllegalStateException::class.java,
        ) { TeeTelemetrySink(first, second).record(UsageTelemetry.AppSession) }

        assertEquals(1, second.events.size)
    }
}
