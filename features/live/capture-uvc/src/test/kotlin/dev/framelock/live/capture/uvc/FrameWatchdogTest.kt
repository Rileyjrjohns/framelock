package dev.framelock.live.capture.uvc

import dev.framelock.testing.ManualClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.seconds

class FrameWatchdogTest {
    private val clock = ManualClock()
    private val watchdog = FrameWatchdog(clock, signalTimeout = 2.seconds, startTimeout = 5.seconds)

    @Test
    fun `nothing is reported before the watchdog starts`() {
        clock.advanceBy(60 * SECOND)

        assertEquals(WatchdogVerdict.OK, watchdog.check())
    }

    @Test
    fun `no first frame within the start timeout means the stream never started`() {
        watchdog.start()

        clock.advanceBy(5 * SECOND - 1)
        assertEquals(WatchdogVerdict.OK, watchdog.check())
        clock.advanceBy(1)
        assertEquals(WatchdogVerdict.NEVER_STARTED, watchdog.check())
    }

    @Test
    fun `frames keep the signal alive`() {
        watchdog.start()
        watchdog.onFrame()

        repeat(10) {
            clock.advanceBy(SECOND)
            assertEquals(WatchdogVerdict.OK, watchdog.check())
            watchdog.onFrame()
        }
    }

    @Test
    fun `no frame for the signal timeout after the first one means the signal is lost`() {
        watchdog.start()
        clock.advanceBy(3 * SECOND)
        watchdog.onFrame()

        clock.advanceBy(2 * SECOND - 1)
        assertEquals(WatchdogVerdict.OK, watchdog.check())
        clock.advanceBy(1)
        assertEquals(WatchdogVerdict.SIGNAL_LOST, watchdog.check())
    }

    @Test
    fun `a verdict is given once`() {
        watchdog.start()
        clock.advanceBy(6 * SECOND)
        assertEquals(WatchdogVerdict.NEVER_STARTED, watchdog.check())

        assertEquals(WatchdogVerdict.OK, watchdog.check())
    }

    @Test
    fun `stop disarms the watchdog`() {
        watchdog.start()
        watchdog.onFrame()
        watchdog.stop()

        clock.advanceBy(60 * SECOND)

        assertEquals(WatchdogVerdict.OK, watchdog.check())
    }

    @Test
    fun `start re-arms the watchdog for a new stream`() {
        watchdog.start()
        watchdog.onFrame()
        clock.advanceBy(3 * SECOND)
        assertEquals(WatchdogVerdict.SIGNAL_LOST, watchdog.check())

        watchdog.start()
        clock.advanceBy(3 * SECOND)

        assertEquals(WatchdogVerdict.OK, watchdog.check())
    }

    @Test
    fun `timeouts must be positive`() {
        assertThrows<IllegalArgumentException> { FrameWatchdog(clock, 0.seconds, 5.seconds) }
        assertThrows<IllegalArgumentException> { FrameWatchdog(clock, 2.seconds, 0.seconds) }
    }

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
