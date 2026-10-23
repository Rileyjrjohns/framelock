package dev.framelock.testing

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.models.domain.telemetry.ModelTelemetry
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

        sink.record(UsageTelemetry.AppSession)
        sink.record(UsageTelemetry.ScreenViewed(ScreenId.LIVE))
        sink.record(UsageTelemetry.UiInteraction(ElementId.START))
        sink.record(ModelTelemetry.ModelSwapped(null, ModelVersion("v1")))

        assertEquals(
            listOf(
                UsageTelemetry.AppSession,
                UsageTelemetry.ScreenViewed(ScreenId.LIVE),
                UsageTelemetry.UiInteraction(ElementId.START),
                ModelTelemetry.ModelSwapped(null, ModelVersion("v1")),
            ),
            sink.events,
        )
    }

    @Test
    fun `injectable failure`() {
        val failure = IllegalStateException("queue full")
        sink.record(UsageTelemetry.AppSession)

        sink.failWith(failure)
        assertSame(
            failure,
            assertThrows<IllegalStateException> { sink.record(UsageTelemetry.ScreenViewed(ScreenId.MODELS)) },
        )
        assertEquals(listOf<TelemetryEvent>(UsageTelemetry.AppSession), sink.events)

        sink.failWith(null)
        sink.record(UsageTelemetry.ScreenViewed(ScreenId.MODELS))
        assertEquals(listOf(UsageTelemetry.AppSession, UsageTelemetry.ScreenViewed(ScreenId.MODELS)), sink.events)
    }

    @Test
    fun `records events from several threads without loss`() {
        val start = CountDownLatch(1)
        val threads =
            List(THREADS) {
                thread {
                    start.await()
                    repeat(EVENTS_PER_THREAD) { sink.record(UsageTelemetry.AppSession) }
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
