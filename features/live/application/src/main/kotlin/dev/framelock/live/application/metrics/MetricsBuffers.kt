package dev.framelock.live.application.metrics

import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId

internal const val NANOS_PER_MILLI = 1_000_000.0

/**
 * The samples of one metric in one window, in a fixed array. Past [capacity] samples the extra ones are not kept,
 * so percentiles use the first [capacity]; callers keep their own exact counts. Not thread-safe: the aggregator
 * guards it.
 */
internal class SampleBuffer(
    capacity: Int,
) {
    private val values = LongArray(capacity)
    private var size = 0

    fun add(nanos: Long) {
        if (size < values.size) values[size++] = nanos
    }

    fun clear() {
        size = 0
    }

    /** Nearest-rank percentile in milliseconds, with no integer truncation; null when there is no sample. */
    fun percentileMs(
        percentile: Int,
        scratch: LongArray,
    ): Double? = Percentiles.nearestRank(values, size, percentile, scratch)?.let { it / NANOS_PER_MILLI }
}

/**
 * Capture times of frames whose result is out but not yet rendered, in a small ring: only the displayed frame is
 * ever rendered (ADR-001), so a handful of entries is enough and the oldest is overwritten. Not thread-safe: the
 * aggregator guards it.
 */
internal class PendingRenders(
    capacity: Int,
) {
    // A session id is at least 1, so 0 marks a free slot.
    private val sessions = LongArray(capacity)
    private val frames = LongArray(capacity)
    private val captureNanos = LongArray(capacity)
    private var next = 0

    fun put(
        session: SessionId,
        frame: FrameId,
        captureNanos: Long,
    ) {
        sessions[next] = session.value
        frames[next] = frame.value
        this.captureNanos[next] = captureNanos
        next = (next + 1) % sessions.size
    }

    fun clear() {
        sessions.fill(FREE)
        next = 0
    }

    /** Removes and returns the capture time of this frame, or [NOT_PENDING] when it is unknown, stale or taken. */
    fun take(
        session: SessionId,
        frame: FrameId,
    ): Long {
        for (slot in sessions.indices) {
            if (sessions[slot] == session.value && frames[slot] == frame.value) {
                sessions[slot] = FREE
                return captureNanos[slot]
            }
        }
        return NOT_PENDING
    }

    companion object {
        const val NOT_PENDING = Long.MIN_VALUE
        private const val FREE = 0L
    }
}
