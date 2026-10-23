package dev.framelock.core.application.pipeline

import dev.framelock.core.domain.telemetry.TelemetryEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pipeline feeds MetricsAggregator; telemetry gets windows, never per-frame events (ADR-004, ADR-011). */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineMetricsTest {
    @Test
    fun `every received frame, dropped ones included, is reported to the aggregator`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) h.emit(h.frame(1), h.frame(2), h.frame(3)) }
            h.emit(h.frame(0))
            runCurrent()

            val window = closeWindow(h)

            assertEquals(4.0, window.inputFps * WINDOW_SECONDS, 1e-9)
        }

    @Test
    fun `dropped frames reported match the pipeline's drop count`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) h.emit(h.frame(1), h.frame(2), h.frame(3)) }
            h.emit(h.frame(0))
            runCurrent()

            val window = closeWindow(h)

            assertEquals(2, window.droppedFrames)
        }

    @Test
    fun `inference duration reported equals the engine's measured value`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()

            val window = closeWindow(h)

            assertEquals(5.0, window.inferenceMsP50)
        }

    @Test
    fun `capture timestamp is passed through to the aggregator`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            val frame = h.frame(0)
            h.emit(frame)
            runCurrent()

            h.metrics.onFrameRendered(frame.id, frame.sessionId, frame.captureTimestampNanos + 40_000_000L)
            val window = closeWindow(h)

            assertEquals(40.0, window.e2eMsP50)
        }

    @Test
    fun `no per-frame event reaches telemetry over 1000 frames`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            repeat(1000) { id ->
                h.emit(h.frame(id.toLong()))
                advanceTimeBy(FRAME_MS)
                runCurrent()
            }

            val perFrame = h.telemetry.filterNot { it is TelemetryEvent.PipelineWindow }
            assertEquals(emptyList<TelemetryEvent>(), perFrame)
            assertTrue(h.telemetry.size <= 1000 * FRAME_MS / WINDOW_MS + 1, "${h.telemetry.size} events")
        }

    private fun kotlinx.coroutines.test.TestScope.closeWindow(h: PipelineHarness): TelemetryEvent.PipelineWindow {
        advanceTimeBy(WINDOW_MS)
        runCurrent()
        h.metrics.tick()
        return h.telemetry.filterIsInstance<TelemetryEvent.PipelineWindow>().single()
    }

    private companion object {
        const val WINDOW_MS = 10_000L
        const val WINDOW_SECONDS = 10.0
        const val FRAME_MS = 33L
    }
}
