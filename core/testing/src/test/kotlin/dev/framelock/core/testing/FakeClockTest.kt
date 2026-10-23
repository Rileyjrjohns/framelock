package dev.framelock.core.testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FakeClockTest {
    @Test
    fun `manual clock starts at the configured time`() {
        assertEquals(0L, ManualClock().nowNanos())
        assertEquals(42L, ManualClock(startNanos = 42).nowNanos())
    }

    @Test
    fun `advanceBy moves time forward`() {
        val clock = ManualClock(startNanos = 10)

        clock.advanceBy(5)
        assertEquals(15L, clock.nowNanos())

        clock.advanceBy(0)
        assertEquals(15L, clock.nowNanos())

        clock.set(100)
        assertEquals(100L, clock.nowNanos())
    }

    @Test
    fun `time never goes backwards`() {
        val clock = ManualClock(startNanos = 50)

        assertThrows<IllegalArgumentException> { clock.advanceBy(-1) }
        assertThrows<IllegalArgumentException> { clock.set(49) }
        assertThrows<IllegalArgumentException> { ManualClock(startNanos = -1) }

        assertEquals(50L, clock.nowNanos())
        clock.set(50)
        assertEquals(50L, clock.nowNanos())
    }

    @Test
    fun `time-source clock follows virtual time`() =
        runTest {
            val clock = FakeClock { testScheduler.currentTime * NANOS_PER_MILLI }
            assertEquals(0L, clock.nowNanos())

            delay(250)
            assertEquals(250 * NANOS_PER_MILLI, clock.nowNanos())

            delay(750)
            assertEquals(1_000 * NANOS_PER_MILLI, clock.nowNanos())
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
