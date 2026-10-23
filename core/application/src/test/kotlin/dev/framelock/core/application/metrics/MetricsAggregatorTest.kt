package dev.framelock.core.application.metrics

import dev.framelock.core.domain.frame.FrameId
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.telemetry.TelemetryEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class MetricsAggregatorTest {
    private val clock = ManualClock()
    private val events = mutableListOf<TelemetryEvent>()
    private val aggregator = MetricsAggregator(clock, { events += it })

    @Test
    fun `emits one PipelineWindow per 10 s window`() {
        aggregator.onFrameReceived()
        clock.now = 5 * SECOND
        aggregator.onFrameReceived()
        assertEquals(0, events.size)

        clock.now = 12 * SECOND
        aggregator.onFrameReceived()
        assertEquals(1, windows().size)

        clock.now = 25 * SECOND
        aggregator.onFrameReceived()
        assertEquals(2, windows().size)
    }

    @Test
    fun `nothing is emitted before the first window closes`() {
        aggregator.onFrameReceived()
        aggregator.onInferenceCompleted(FrameId(0), SESSION, 0, 5 * MILLI)
        aggregator.onFrameDropped(DropReason.CONFLATED)

        clock.now = WINDOW - 1
        aggregator.onFrameReceived()
        aggregator.tick()

        assertEquals(emptyList<TelemetryEvent>(), events)
    }

    @Test
    fun `sample at exactly 10 s belongs to the next window`() {
        repeat(3) { aggregator.onFrameReceived() }

        clock.now = WINDOW
        aggregator.onFrameReceived()

        assertEquals(1, events.size)
        assertEquals(0.3, windows().single().inputFps, EPSILON)

        clock.now = 2 * WINDOW
        aggregator.tick()

        assertEquals(2, events.size)
        assertEquals(0.1, windows()[1].inputFps, EPSILON)
    }

    @Test
    fun `inference p50 and p95 match the injected durations`() {
        aggregator.onFrameReceived()
        // Completed out of order on purpose: 1 ms to 100 ms.
        (0 until 100).shuffled(kotlin.random.Random(SEED)).forEach { index ->
            aggregator.onInferenceCompleted(FrameId(index.toLong()), SESSION, 0, (index + 1) * MILLI)
        }

        closeWindow()

        val window = windows().single()
        assertEquals(50.0, window.inferenceMsP50!!, EPSILON)
        assertEquals(95.0, window.inferenceMsP95!!, EPSILON)
    }

    @Test
    fun `inputFps is frames received divided by window seconds`() {
        repeat(50) { aggregator.onFrameReceived() }

        closeWindow()

        assertEquals(5.0, windows().single().inputFps, EPSILON)
    }

    @Test
    fun `inferenceFps is inferences completed divided by window seconds`() {
        repeat(30) { aggregator.onFrameReceived() }
        repeat(20) { aggregator.onInferenceCompleted(FrameId(it.toLong()), SESSION, 0, MILLI) }

        closeWindow()

        assertEquals(2.0, windows().single().inferenceFps, EPSILON)
    }

    @Test
    fun `droppedFrames counts the window and resets`() {
        repeat(10) { aggregator.onFrameReceived() }
        aggregator.onFrameDropped(DropReason.CONFLATED)
        aggregator.onFrameDropped(DropReason.LATE)
        aggregator.onFrameDropped(DropReason.STALE_SESSION)

        closeWindow()

        clock.now = WINDOW + SECOND
        repeat(10) { aggregator.onFrameReceived() }
        aggregator.onFrameDropped(DropReason.PAUSED)

        clock.now = 2 * WINDOW
        aggregator.tick()

        assertEquals(listOf(3, 1), windows().map { it.droppedFrames })
    }

    @Test
    fun `e2e latency is render minus capture on the same clock`() {
        aggregator.onFrameReceived()
        clock.now = 2 * SECOND
        aggregator.onInferenceCompleted(FrameId(1), SESSION, captureNanos = 1 * SECOND, inferenceNanos = 20 * MILLI)
        aggregator.onFrameRendered(FrameId(1), SESSION, renderNanos = 1 * SECOND + 250 * MILLI)
        aggregator.onInferenceCompleted(FrameId(2), SESSION, captureNanos = 2 * SECOND, inferenceNanos = 20 * MILLI)
        aggregator.onFrameRendered(FrameId(2), SESSION, renderNanos = 2 * SECOND + 300 * MILLI)

        closeWindow()

        val window = windows().single()
        // Two samples, 250 ms and 300 ms: nearest rank 1 and 2.
        assertEquals(250.0, window.e2eMsP50!!, EPSILON)
        assertEquals(300.0, window.e2eMsP95!!, EPSILON)
    }

    @Test
    fun `render report for an unknown or stale frame is ignored`() {
        aggregator.onFrameReceived()
        // Never completed.
        aggregator.onFrameRendered(FrameId(99), SESSION, renderNanos = SECOND)
        // Completed in session 1, reported by session 2.
        aggregator.onInferenceCompleted(FrameId(1), SESSION, captureNanos = 0, inferenceNanos = MILLI)
        aggregator.onFrameRendered(FrameId(1), SESSION.next(), renderNanos = SECOND)
        // Overtaken by many newer results: only the displayed frame is rendered, so an old one is stale.
        aggregator.onInferenceCompleted(FrameId(2), SESSION, captureNanos = 0, inferenceNanos = MILLI)
        repeat(
            100,
        ) { aggregator.onInferenceCompleted(FrameId(3L + it), SESSION, captureNanos = 0, inferenceNanos = MILLI) }
        aggregator.onFrameRendered(FrameId(2), SESSION, renderNanos = SECOND)

        closeWindow()

        val window = windows().single()
        assertNull(window.e2eMsP50)
        assertNull(window.e2eMsP95)
    }

    @Test
    fun `render report of the latest frame is kept when older ones were overtaken`() {
        aggregator.onFrameReceived()
        repeat(
            100,
        ) { aggregator.onInferenceCompleted(FrameId(it.toLong()), SESSION, captureNanos = 0, inferenceNanos = MILLI) }

        aggregator.onFrameRendered(FrameId(99), SESSION, renderNanos = 40 * MILLI)

        closeWindow()

        assertEquals(40.0, windows().single().e2eMsP50!!, EPSILON)
    }

    @Test
    fun `a frame's render is recorded once`() {
        aggregator.onFrameReceived()
        aggregator.onInferenceCompleted(FrameId(1), SESSION, captureNanos = 0, inferenceNanos = MILLI)
        aggregator.onFrameRendered(FrameId(1), SESSION, renderNanos = 30 * MILLI)
        // The overlay is drawn again for the same frame (for example when the HUD changes): not a new sample.
        aggregator.onFrameRendered(FrameId(1), SESSION, renderNanos = 900 * MILLI)

        closeWindow()

        val window = windows().single()
        assertEquals(30.0, window.e2eMsP50!!, EPSILON)
        assertEquals(30.0, window.e2eMsP95!!, EPSILON)
    }

    @Test
    fun `negative e2e is excluded from percentiles`() {
        aggregator.onFrameReceived()
        // Rendered "before" it was captured: a clock-base mismatch, not a latency.
        aggregator.onInferenceCompleted(FrameId(1), SESSION, captureNanos = 5 * SECOND, inferenceNanos = MILLI)
        aggregator.onFrameRendered(FrameId(1), SESSION, renderNanos = 4 * SECOND)

        closeWindow()

        val window = windows().single()
        assertNull(window.e2eMsP50)
        assertNull(window.e2eMsP95)
    }

    @Test
    fun `negative e2e does not hide valid samples`() {
        aggregator.onFrameReceived()
        aggregator.onInferenceCompleted(FrameId(1), SESSION, captureNanos = 5 * SECOND, inferenceNanos = MILLI)
        aggregator.onFrameRendered(FrameId(1), SESSION, renderNanos = 4 * SECOND)
        aggregator.onInferenceCompleted(FrameId(2), SESSION, captureNanos = SECOND, inferenceNanos = MILLI)
        aggregator.onFrameRendered(FrameId(2), SESSION, renderNanos = SECOND + 500 * MILLI)

        closeWindow()

        val window = windows().single()
        assertEquals(500.0, window.e2eMsP50!!, EPSILON)
        assertEquals(500.0, window.e2eMsP95!!, EPSILON)
    }

    @Test
    fun `empty window emits nothing`() {
        aggregator.tick()
        clock.now = WINDOW
        aggregator.tick()
        clock.now = 2 * WINDOW
        aggregator.tick()
        // Drops and results without any received frame do not make a window either.
        aggregator.onFrameDropped(DropReason.STOPPED)
        aggregator.onInferenceCompleted(FrameId(0), SESSION, 0, MILLI)
        clock.now = 3 * WINDOW
        aggregator.tick()

        assertEquals(emptyList<TelemetryEvent>(), events)
    }

    @Test
    fun `windows stay aligned to the first call after skipped empty windows`() {
        aggregator.onFrameReceived()
        clock.now = WINDOW
        aggregator.tick()
        assertEquals(1, events.size)

        // Four empty windows go by.
        clock.now = 55 * SECOND
        aggregator.tick()
        assertEquals(1, events.size)

        // 55 s is in the window that started at 50 s, which closes at 60 s, not at 65 s.
        clock.now = 56 * SECOND
        aggregator.onFrameReceived()
        clock.now = 59 * SECOND
        aggregator.onFrameReceived()
        clock.now = 60 * SECOND
        aggregator.tick()

        assertEquals(2, events.size)
        assertEquals(0.2, windows()[1].inputFps, EPSILON)
    }

    @Test
    fun `window with input but no inference has inferenceFps 0 and no latency percentiles`() {
        repeat(5) { aggregator.onFrameReceived() }

        closeWindow()

        val window = windows().single()
        assertEquals(0.5, window.inputFps, EPSILON)
        assertEquals(0.0, window.inferenceFps, EPSILON)
        assertNull(window.inferenceMsP50)
        assertNull(window.inferenceMsP95)
        assertNull(window.e2eMsP50)
        assertNull(window.e2eMsP95)
    }

    @Test
    fun `sample buffer overflow keeps counts exact and caps percentile samples`() {
        val capped = MetricsAggregator(clock, { events += it }, maxSamplesPerWindow = 10)
        repeat(25) { capped.onFrameReceived() }
        repeat(25) { index ->
            // Frame n takes (n + 1) ms and is rendered (n + 1) * 10 ms after its capture.
            capped.onInferenceCompleted(
                FrameId(index.toLong()),
                SESSION,
                captureNanos = 0,
                inferenceNanos =
                    (index + 1) * MILLI,
            )
            capped.onFrameRendered(FrameId(index.toLong()), SESSION, renderNanos = (index + 1) * 10 * MILLI)
        }
        repeat(7) { capped.onFrameDropped(DropReason.CONFLATED) }

        clock.now = WINDOW
        capped.tick()

        val window = windows().single()
        assertEquals(2.5, window.inputFps, EPSILON)
        assertEquals(2.5, window.inferenceFps, EPSILON)
        assertEquals(7, window.droppedFrames)
        // Only the first 10 samples (1..10 ms and 10..100 ms) take part in the percentiles.
        assertEquals(5.0, window.inferenceMsP50!!, EPSILON)
        assertEquals(10.0, window.inferenceMsP95!!, EPSILON)
        assertEquals(50.0, window.e2eMsP50!!, EPSILON)
        assertEquals(100.0, window.e2eMsP95!!, EPSILON)
    }

    @Test
    fun `only PipelineWindow events are published, never per frame`() {
        // 1000 frames, 25 ms apart: 24.975 s of video across three windows.
        repeat(1000) { index ->
            clock.now = index * 25 * MILLI
            aggregator.onFrameReceived()
            aggregator.onInferenceCompleted(FrameId(index.toLong()), SESSION, clock.now, 8 * MILLI)
            aggregator.onFrameRendered(FrameId(index.toLong()), SESSION, clock.now + 30 * MILLI)
            if (index % 10 == 0) aggregator.onFrameDropped(DropReason.CONFLATED)
            if (index % SECOND_IN_FRAMES == 0) aggregator.tick()
        }
        // The first two windows were closed by frames at 10 s and 20 s.
        assertEquals(2, events.size)

        clock.now = 3 * WINDOW
        aggregator.tick()

        assertEquals(3, events.size)
        assertTrue(events.all { it is TelemetryEvent.PipelineWindow })
        assertEquals(40.0, windows().first().inputFps, EPSILON)
    }

    @Test
    fun `nanos are converted to milliseconds without truncation error`() {
        aggregator.onFrameReceived()
        aggregator.onInferenceCompleted(FrameId(1), SESSION, captureNanos = 0, inferenceNanos = 1_999_999)
        aggregator.onFrameRendered(FrameId(1), SESSION, renderNanos = 2_345_678)

        closeWindow()

        val window = windows().single()
        assertEquals(1.999999, window.inferenceMsP50!!, 1e-12)
        assertEquals(1.999999, window.inferenceMsP95!!, 1e-12)
        assertEquals(2.345678, window.e2eMsP50!!, 1e-12)
        assertEquals(2.345678, window.e2eMsP95!!, 1e-12)
    }

    @Test
    fun `live metrics count drops by reason`() {
        repeat(3) { aggregator.onFrameDropped(DropReason.CONFLATED) }
        aggregator.onFrameDropped(DropReason.LATE)
        repeat(2) { aggregator.onFrameDropped(DropReason.STALE_SESSION) }

        clock.now = SECOND
        aggregator.tick()

        val first = aggregator.live.value
        assertEquals(6L, first.droppedFrames)
        assertEquals(
            mapOf(DropReason.CONFLATED to 3L, DropReason.LATE to 1L, DropReason.STALE_SESSION to 2L),
            first.dropsByReason,
        )

        // Cumulative: a window closing does not reset the HUD totals.
        aggregator.onFrameDropped(DropReason.PAUSED)
        aggregator.onFrameDropped(DropReason.CONFLATED)
        clock.now = WINDOW + SECOND
        aggregator.tick()

        val second = aggregator.live.value
        assertEquals(8L, second.droppedFrames)
        assertEquals(4L, second.dropsByReason[DropReason.CONFLATED])
        assertEquals(1L, second.dropsByReason[DropReason.PAUSED])
        assertEquals(second.droppedFrames, second.dropsByReason.values.sum())
    }

    @Test
    fun `live metrics are empty until the first tick`() {
        repeat(5) { aggregator.onFrameReceived() }
        aggregator.onFrameDropped(DropReason.CONFLATED)

        assertEquals(LiveMetrics.EMPTY, aggregator.live.value)
    }

    @Test
    fun `live metrics report rates over the last tick interval and the last latencies`() {
        aggregator.tick()

        clock.now = SECOND
        repeat(30) { aggregator.onFrameReceived() }
        repeat(20) { aggregator.onInferenceCompleted(FrameId(it.toLong()), SESSION, 0, (it + 1) * MILLI) }
        aggregator.onFrameRendered(FrameId(19), SESSION, renderNanos = 80 * MILLI)
        aggregator.tick()

        val first = aggregator.live.value
        assertEquals(30.0, first.inputFps, EPSILON)
        assertEquals(20.0, first.inferenceFps, EPSILON)
        assertEquals(20.0, first.inferenceMs!!, EPSILON)
        assertEquals(80.0, first.e2eMs!!, EPSILON)

        // Half a second later, fewer frames: the rates cover the new interval only, the latencies stay the last ones.
        clock.now = SECOND + 500 * MILLI
        repeat(5) { aggregator.onFrameReceived() }
        aggregator.onInferenceCompleted(FrameId(20), SESSION, 0, 12 * MILLI)
        aggregator.tick()

        val second = aggregator.live.value
        assertEquals(10.0, second.inputFps, EPSILON)
        assertEquals(2.0, second.inferenceFps, EPSILON)
        assertEquals(12.0, second.inferenceMs!!, EPSILON)
        assertEquals(80.0, second.e2eMs!!, EPSILON)
    }

    @Test
    fun `live latencies are null before any sample`() {
        aggregator.tick()
        clock.now = SECOND
        repeat(5) { aggregator.onFrameReceived() }
        aggregator.tick()

        val live = aggregator.live.value
        assertEquals(5.0, live.inputFps, EPSILON)
        assertNull(live.inferenceMs)
        assertNull(live.e2eMs)
    }

    @Test
    fun `invalid configuration is rejected`() {
        assertThrows<IllegalArgumentException> { MetricsAggregator(clock, { }, windowNanos = 0) }
        assertThrows<IllegalArgumentException> { MetricsAggregator(clock, { }, maxSamplesPerWindow = 0) }
    }

    @Test
    fun `concurrent calls from two threads keep counts exact`() {
        val perThread = 50_000
        val start = CountDownLatch(1)
        val inputThread =
            thread {
                start.await()
                repeat(perThread) { aggregator.onFrameReceived() }
            }
        val inferenceThread =
            thread {
                start.await()
                repeat(perThread) { index ->
                    aggregator.onInferenceCompleted(FrameId(index.toLong()), SESSION, 0, MILLI)
                    aggregator.onFrameDropped(DropReason.CONFLATED)
                }
            }

        start.countDown()
        inputThread.join()
        inferenceThread.join()
        closeWindow()

        val window = windows().single()
        assertEquals(perThread / 10.0, window.inputFps, EPSILON)
        assertEquals(perThread / 10.0, window.inferenceFps, EPSILON)
        assertEquals(perThread, window.droppedFrames)
        assertEquals(1.0, window.inferenceMsP50!!, EPSILON)
    }

    private fun closeWindow() {
        clock.now = WINDOW
        aggregator.tick()
    }

    private fun windows() = events.filterIsInstance<TelemetryEvent.PipelineWindow>()

    private companion object {
        const val MILLI = 1_000_000L
        const val SECOND = 1_000 * MILLI
        const val WINDOW = 10 * SECOND
        const val EPSILON = 1e-9
        const val SEED = 11
        const val SECOND_IN_FRAMES = 40
        val SESSION = SessionId.FIRST
    }
}
