package dev.framelock.live.capture.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackScheduleTest {
    private val ms = 1_000_000L

    @Test
    fun `the first frame is due at once whatever its timestamp`() {
        val schedule = PlaybackSchedule()

        assertEquals(0, schedule.waitNanos(ptsUs = 500_000, nowNanos = 7_000 * ms))
    }

    @Test
    fun `later frames are due at their offset from the first`() {
        val schedule = PlaybackSchedule()
        schedule.waitNanos(0, nowNanos = 1_000 * ms)

        // 10 ms after the first frame was shown, the frame stamped 33.333 ms is due in 23.333 ms.
        assertEquals(23_333_000, schedule.waitNanos(33_333, nowNanos = 1_010 * ms))
    }

    @Test
    fun `a late frame is due now`() {
        val schedule = PlaybackSchedule()
        schedule.waitNanos(0, nowNanos = 0)

        assertEquals(0, schedule.waitNanos(33_333, nowNanos = 80 * ms))
    }

    @Test
    fun `a long stall re-anchors instead of bursting`() {
        val schedule = PlaybackSchedule(maxLagNanos = 500 * ms)
        schedule.waitNanos(0, nowNanos = 0)
        // Five seconds late: due now, and the schedule restarts from here.
        assertEquals(0, schedule.waitNanos(33_333, nowNanos = 5_000 * ms))

        assertEquals(33_333_000, schedule.waitNanos(66_666, nowNanos = 5_000 * ms))
    }

    @Test
    fun `a lag within the limit is caught up, not re-anchored`() {
        val schedule = PlaybackSchedule(maxLagNanos = 500 * ms)
        schedule.waitNanos(0, nowNanos = 0)
        schedule.waitNanos(33_333, nowNanos = 300 * ms)

        // Still anchored on the first frame: the frame stamped 66.666 ms is long due.
        assertEquals(0, schedule.waitNanos(66_666, nowNanos = 300 * ms))
        // The one stamped 400 ms is due at 400 ms.
        assertEquals(100 * ms, schedule.waitNanos(400_000, nowNanos = 300 * ms))
    }

    @Test
    fun `a loop continues one frame step after the last frame`() {
        val schedule = PlaybackSchedule()
        schedule.waitNanos(0, nowNanos = 0)
        schedule.waitNanos(40_000, nowNanos = 40 * ms)
        schedule.waitNanos(80_000, nowNanos = 80 * ms)

        schedule.looped()

        // The file restarts at its first timestamp (0): due 40 ms after the last frame (80 ms), i.e. at 120 ms.
        assertEquals(40 * ms, schedule.waitNanos(0, nowNanos = 80 * ms))
        assertEquals(80 * ms, schedule.waitNanos(40_000, nowNanos = 80 * ms))
    }

    @Test
    fun `loops accumulate`() {
        val schedule = PlaybackSchedule()
        schedule.waitNanos(1_000_000, nowNanos = 0)
        schedule.waitNanos(1_050_000, nowNanos = 50 * ms)
        schedule.looped()
        schedule.waitNanos(1_000_000, nowNanos = 100 * ms)
        schedule.waitNanos(1_050_000, nowNanos = 150 * ms)
        schedule.looped()

        // Third pass, first frame: 2 x (50 ms + 50 ms step) after the very first frame.
        assertEquals(200 * ms, schedule.waitNanos(1_000_000, nowNanos = 150 * ms) + 150 * ms)
    }

    @Test
    fun `the frame step defaults to 30 fps until two timestamps are seen`() {
        val schedule = PlaybackSchedule()
        schedule.waitNanos(0, nowNanos = 0)

        schedule.looped()

        assertEquals(PlaybackSchedule.DEFAULT_STEP_US * 1_000, schedule.waitNanos(0, nowNanos = 0))
    }
}
