package dev.framelock.telemetry.application

import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.live.domain.telemetry.LiveTelemetry
import dev.framelock.testing.FakeTelemetrySink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class TelemetryRecorderTest {
    private val sink = FakeTelemetrySink()
    private val recorder = TelemetryRecorder(sink)

    @Test
    fun `accepted events are forwarded unchanged`() {
        val event = UsageTelemetry.ScreenViewed(ScreenId.LIVE)

        recorder.record(event)

        assertEquals(listOf(event), sink.events)
    }

    @Test
    fun `rejected events are not forwarded and are counted`() {
        val negativeFps =
            LiveTelemetry.PipelineWindow(null, null, null, null, inputFps = -1.0, inferenceFps = 0.0, droppedFrames = 0)
        recorder.record(negativeFps)
        recorder.record(object : TelemetryEvent {})

        assertEquals(emptyList<TelemetryEvent>(), sink.events)
        assertEquals(2L, recorder.rejectedCount)
    }

    @Test
    fun `sink exception does not propagate`() {
        sink.failWith(IllegalStateException("disk full"))

        assertDoesNotThrow { recorder.record(UsageTelemetry.AppSession) }
        assertEquals(1L, recorder.failedCount)
    }

    @Test
    fun `track screen and track interaction record their events through the allowlist`() {
        TrackScreen(recorder)(ScreenId.MODELS)
        TrackUiInteraction(recorder)(ElementId.START)

        assertEquals(
            listOf(UsageTelemetry.ScreenViewed(ScreenId.MODELS), UsageTelemetry.UiInteraction(ElementId.START)),
            sink.events,
        )
    }
}
