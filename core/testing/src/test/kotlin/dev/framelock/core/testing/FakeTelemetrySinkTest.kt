package dev.framelock.core.testing

import dev.framelock.core.domain.model.ModelVersion
import dev.framelock.core.domain.telemetry.ElementId
import dev.framelock.core.domain.telemetry.ScreenId
import dev.framelock.core.domain.telemetry.TelemetryEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class FakeTelemetrySinkTest {
    private val sink = FakeTelemetrySink()

    @Test
    fun `records events in order`() {
        assertTrue(sink.events.isEmpty())

        sink.record(TelemetryEvent.AppSession)
        sink.record(TelemetryEvent.ScreenViewed(ScreenId.LIVE))
        sink.record(TelemetryEvent.UiInteraction(ElementId.START))
        sink.record(TelemetryEvent.ModelSwapped(null, ModelVersion("v1")))

        assertEquals(
            listOf(
                TelemetryEvent.AppSession,
                TelemetryEvent.ScreenViewed(ScreenId.LIVE),
                TelemetryEvent.UiInteraction(ElementId.START),
                TelemetryEvent.ModelSwapped(null, ModelVersion("v1")),
            ),
            sink.events,
        )
    }

    @Test
    fun `injectable failure`() {
        val failure = IllegalStateException("queue full")
        sink.record(TelemetryEvent.AppSession)

        sink.failWith(failure)
        assertSame(
            failure,
            assertThrows<IllegalStateException> { sink.record(TelemetryEvent.ScreenViewed(ScreenId.MODELS)) },
        )
        assertEquals(listOf<TelemetryEvent>(TelemetryEvent.AppSession), sink.events)

        sink.failWith(null)
        sink.record(TelemetryEvent.ScreenViewed(ScreenId.MODELS))
        assertEquals(listOf(TelemetryEvent.AppSession, TelemetryEvent.ScreenViewed(ScreenId.MODELS)), sink.events)
    }

    @Test
    fun `records events from several threads without loss`() {
        val start = CountDownLatch(1)
        val threads =
            List(THREADS) {
                thread {
                    start.await()
                    repeat(EVENTS_PER_THREAD) { sink.record(TelemetryEvent.AppSession) }
                }
            }

        start.countDown()
        threads.forEach { it.join(JOIN_TIMEOUT_MILLIS) }

        assertEquals(THREADS * EVENTS_PER_THREAD, sink.events.size)
    }

    private companion object {
        const val THREADS = 4
        const val EVENTS_PER_THREAD = 500
        const val JOIN_TIMEOUT_MILLIS = 10_000L
    }
}
