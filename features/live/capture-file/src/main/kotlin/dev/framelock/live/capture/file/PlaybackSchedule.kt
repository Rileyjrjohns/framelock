package dev.framelock.live.capture.file

/**
 * When each decoded frame of a looping file is due, from its presentation timestamp, so that a file plays at its own
 * rate whatever the decoder's speed. Times are `Clock` nanoseconds; presentation timestamps are microseconds, as
 * `MediaExtractor` reports them. Pure: the decoder thread owns one instance and calls it from there only.
 *
 * - The first frame is due at once and anchors the schedule: a frame whose timestamp is `d` µs after the first is due
 *   `d` µs after that moment.
 * - A frame that is late is due now (no negative wait). When playback falls more than [maxLagNanos] behind (the
 *   process was suspended, the pool was starved), the schedule re-anchors on the current frame instead of bursting
 *   frames to catch up: the input rate stays what the file says.
 * - [looped] is called when the file restarts from its first sample. The next frame continues one frame step after the
 *   last one, so a loop is not a gap and not a repeat.
 */
internal class PlaybackSchedule(
    private val maxLagNanos: Long = DEFAULT_MAX_LAG_NANOS,
) {
    private var anchored = false
    private var anchorNanos = 0L
    private var firstPtsUs = 0L
    private var loopOffsetUs = 0L
    private var lastPtsUs = 0L
    private var stepUs = DEFAULT_STEP_US

    /** Nanoseconds to wait from [nowNanos] before showing the frame stamped [ptsUs]; never negative. */
    fun waitNanos(
        ptsUs: Long,
        nowNanos: Long,
    ): Long {
        if (!anchored) {
            anchored = true
            firstPtsUs = ptsUs
            anchorNanos = nowNanos
        } else if (ptsUs > lastPtsUs) {
            stepUs = ptsUs - lastPtsUs
        }
        lastPtsUs = ptsUs
        val positionNanos = (loopOffsetUs + ptsUs - firstPtsUs) * NANOS_PER_MICRO
        val wait = anchorNanos + positionNanos - nowNanos
        if (wait < -maxLagNanos) {
            anchorNanos = nowNanos - positionNanos
            return 0
        }
        return maxOf(wait, 0)
    }

    /** The file restarted: the next timestamps start again from the file's first one. */
    fun looped() {
        loopOffsetUs += lastPtsUs - firstPtsUs + stepUs
    }

    companion object {
        /** A 30 fps step, used until two timestamps give the real one. */
        const val DEFAULT_STEP_US = 33_333L
        const val DEFAULT_MAX_LAG_NANOS = 1_000_000_000L
        private const val NANOS_PER_MICRO = 1_000L
    }
}
