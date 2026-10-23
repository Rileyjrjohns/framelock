package dev.framelock.core.application.metrics

import dev.framelock.core.domain.frame.FrameId
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.port.Clock
import dev.framelock.core.domain.telemetry.TelemetryEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MEDIAN = 50
private const val TAIL = 95
private const val PENDING_RENDER_CAPACITY = 8
private const val NONE = Long.MIN_VALUE

/**
 * Aggregates pipeline metrics over fixed windows and publishes one [TelemetryEvent.PipelineWindow] per window,
 * never one event per frame (ADR-004). It also keeps [live], the debug HUD values.
 *
 * Windows: the first one starts at the first call of any method. Every call first closes the current window when
 * `now >= windowStart + windowNanos`, publishing it and starting the next at `windowStart + k * windowNanos`: windows
 * stay aligned to the first call, and a window that went by without a received frame is skipped without an event.
 * [tick] makes sure a window closes even when no frame arrives.
 *
 * Counts (received, completed, dropped) are exact. Latency percentiles use at most [maxSamplesPerWindow] samples per
 * window: the first ones.
 *
 * Thread-safe: called from the inference thread, the render thread and whichever thread ticks. Every call takes one
 * short lock and allocates nothing in steady state (ADR-012); only closing a window allocates its event, and
 * [tick] its [LiveMetrics]. [publish] is called on the calling thread after the lock is released, so a slow sink
 * never blocks the other threads.
 */
class MetricsAggregator(
    private val clock: Clock,
    private val publish: (TelemetryEvent) -> Unit,
    private val windowNanos: Long = DEFAULT_WINDOW_NANOS,
    maxSamplesPerWindow: Int = DEFAULT_MAX_SAMPLES_PER_WINDOW,
) {
    init {
        require(windowNanos > 0) { "windowNanos must be > 0, was $windowNanos" }
        require(maxSamplesPerWindow > 0) { "maxSamplesPerWindow must be > 0, was $maxSamplesPerWindow" }
    }

    private val lock = Any()
    private val windowSeconds = windowNanos / NANOS_PER_SECOND

    // Current window. All state below is guarded by [lock].
    private var started = false
    private var windowStartNanos = 0L
    private var received = 0
    private var completed = 0
    private var dropped = 0
    private val inferenceSamples = SampleBuffer(maxSamplesPerWindow)
    private val e2eSamples = SampleBuffer(maxSamplesPerWindow)
    private val scratch = LongArray(maxSamplesPerWindow)
    private val pendingRenders = PendingRenders(PENDING_RENDER_CAPACITY)

    // Live (HUD) state: rates since the last tick, last latencies, cumulative drops.
    private var tickStartNanos = 0L
    private var tickReceived = 0
    private var tickCompleted = 0
    private var lastInferenceNanos = NONE
    private var lastE2eNanos = NONE
    private var totalDropped = 0L
    private val droppedByReason = LongArray(DropReason.entries.size)

    private val liveState = MutableStateFlow(LiveMetrics.EMPTY)

    /** Debug HUD values, updated by [tick]. */
    val live: StateFlow<LiveMetrics> = liveState.asStateFlow()

    fun onFrameReceived() {
        record {
            received++
            tickReceived++
        }
    }

    fun onFrameDropped(reason: DropReason) {
        record {
            dropped++
            totalDropped++
            droppedByReason[reason.ordinal]++
        }
    }

    /**
     * An inference finished. [captureNanos] is the frame's capture time on the [Clock] base, kept until the frame is
     * reported rendered. A negative [inferenceNanos] is not a duration: it is counted but left out of the samples.
     */
    fun onInferenceCompleted(
        frameId: FrameId,
        sessionId: SessionId,
        captureNanos: Long,
        inferenceNanos: Long,
    ) {
        record {
            completed++
            tickCompleted++
            if (inferenceNanos >= 0) {
                inferenceSamples.add(inferenceNanos)
                lastInferenceNanos = inferenceNanos
            }
            pendingRenders.put(sessionId, frameId, captureNanos)
        }
    }

    /**
     * The frame was drawn at [renderNanos] (same clock base as the capture time). The end-to-end latency is the
     * difference with the capture time recorded by [onInferenceCompleted] for this (session, frame). A frame that is
     * unknown, already reported, overtaken by newer results or from another session is ignored; so is a negative
     * latency (clock mismatch).
     */
    fun onFrameRendered(
        frameId: FrameId,
        sessionId: SessionId,
        renderNanos: Long,
    ) {
        record {
            val captureNanos = pendingRenders.take(sessionId, frameId)
            if (captureNanos != PendingRenders.NOT_PENDING) {
                val e2eNanos = renderNanos - captureNanos
                if (e2eNanos >= 0) {
                    e2eSamples.add(e2eNanos)
                    lastE2eNanos = e2eNanos
                }
            }
        }
    }

    /** Closes the window if it is due and refreshes [live] with the rates since the previous tick. */
    fun tick() {
        var closed: TelemetryEvent.PipelineWindow? = null
        var snapshot: LiveMetrics? = null
        synchronized(lock) {
            val now = clock.nowNanos()
            closed = closeWindowIfDue(now)
            snapshot = liveSnapshot(now)
        }
        closed?.let(publish)
        // Set outside the lock: a collector that runs inline must not hold up the other threads.
        snapshot?.let { liveState.value = it }
    }

    private inline fun record(update: () -> Unit) {
        val closed =
            synchronized(lock) {
                val due = closeWindowIfDue(clock.nowNanos())
                update()
                due
            }
        if (closed != null) publish(closed)
    }

    private fun closeWindowIfDue(now: Long): TelemetryEvent.PipelineWindow? {
        if (!started) {
            started = true
            windowStartNanos = now
            tickStartNanos = now
        }
        val elapsed = now - windowStartNanos
        if (elapsed < windowNanos) return null

        val window = if (received > 0) windowEvent() else null
        received = 0
        completed = 0
        dropped = 0
        inferenceSamples.clear()
        e2eSamples.clear()
        windowStartNanos += elapsed / windowNanos * windowNanos
        return window
    }

    private fun windowEvent() =
        TelemetryEvent.PipelineWindow(
            inferenceMsP50 = inferenceSamples.percentileMs(MEDIAN, scratch),
            inferenceMsP95 = inferenceSamples.percentileMs(TAIL, scratch),
            e2eMsP50 = e2eSamples.percentileMs(MEDIAN, scratch),
            e2eMsP95 = e2eSamples.percentileMs(TAIL, scratch),
            inputFps = received / windowSeconds,
            inferenceFps = completed / windowSeconds,
            droppedFrames = dropped,
        )

    /** The HUD values for the interval ending at [now], or null when no time went by since the last tick. */
    private fun liveSnapshot(now: Long): LiveMetrics? {
        val seconds = (now - tickStartNanos) / NANOS_PER_SECOND
        if (seconds <= 0.0) return null

        val snapshot =
            LiveMetrics(
                inputFps = tickReceived / seconds,
                inferenceFps = tickCompleted / seconds,
                inferenceMs = lastInferenceNanos.toMillisOrNull(),
                e2eMs = lastE2eNanos.toMillisOrNull(),
                droppedFrames = totalDropped,
                dropsByReason =
                    buildMap {
                        for (reason in DropReason.entries) {
                            if (droppedByReason[reason.ordinal] > 0) put(reason, droppedByReason[reason.ordinal])
                        }
                    },
            )
        tickStartNanos = now
        tickReceived = 0
        tickCompleted = 0
        return snapshot
    }

    private fun Long.toMillisOrNull(): Double? = if (this == NONE) null else this / NANOS_PER_MILLI

    companion object {
        const val DEFAULT_WINDOW_NANOS = 10_000_000_000L
        const val DEFAULT_MAX_SAMPLES_PER_WINDOW = 2048
    }
}
