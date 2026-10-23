package dev.framelock.live.capture.uvc

import dev.framelock.kernel.port.Clock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

internal enum class WatchdogVerdict {
    OK,

    /** No frame at all arrived within the start timeout. */
    NEVER_STARTED,

    /** Frames were flowing and stopped for longer than the signal timeout. */
    SIGNAL_LOST,
}

/**
 * Detects a stream that went silent. A capture card with no video input usually stops sending frames; the library
 * reports nothing in that case. A card that keeps sending a placeholder picture is not detected (to be checked on the
 * real card).
 *
 * Driven by the [Clock] port: [start] when the preview starts, [onFrame] on each frame (any thread), [check] from a
 * periodic task. A verdict other than [WatchdogVerdict.OK] is given once; call [start] again for the next stream.
 */
internal class FrameWatchdog(
    private val clock: Clock,
    signalTimeout: Duration,
    startTimeout: Duration,
) {
    init {
        require(signalTimeout.isPositive() && startTimeout.isPositive()) {
            "Timeouts must be positive, were $signalTimeout and $startTimeout"
        }
    }

    private val signalTimeoutNanos = signalTimeout.inWholeNanoseconds
    private val startTimeoutNanos = startTimeout.inWholeNanoseconds

    /** Time of the last frame, or of the start while no frame arrived; [DISARMED] when not watching. */
    private val lastActivity = AtomicLong(DISARMED)

    @Volatile private var sawFrame = false

    fun start() {
        sawFrame = false
        lastActivity.set(clock.nowNanos())
    }

    fun stop() {
        lastActivity.set(DISARMED)
    }

    fun onFrame() {
        if (lastActivity.get() == DISARMED) return
        sawFrame = true
        lastActivity.set(clock.nowNanos())
    }

    fun check(): WatchdogVerdict {
        val last = lastActivity.get()
        if (last == DISARMED) return WatchdogVerdict.OK
        val silence = clock.nowNanos() - last
        val verdict =
            when {
                sawFrame && silence >= signalTimeoutNanos -> WatchdogVerdict.SIGNAL_LOST
                !sawFrame && silence >= startTimeoutNanos -> WatchdogVerdict.NEVER_STARTED
                else -> WatchdogVerdict.OK
            }
        if (verdict != WatchdogVerdict.OK) lastActivity.compareAndSet(last, DISARMED)
        return verdict
    }

    private companion object {
        const val DISARMED = -1L
    }
}
