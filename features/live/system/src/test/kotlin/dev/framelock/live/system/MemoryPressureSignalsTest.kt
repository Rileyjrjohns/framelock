package dev.framelock.live.system

import dev.framelock.live.domain.system.MemoryPressure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class MemoryPressureSignalsTest {
    private val mb = 1_000_000L

    @Test
    fun `running trim levels map to moderate and critical`() {
        assertEquals(
            MemoryPressure.MODERATE,
            TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE),
        )
        assertEquals(
            MemoryPressure.MODERATE,
            TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW),
        )
        assertEquals(
            MemoryPressure.CRITICAL,
            TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
        )
    }

    @Test
    fun `hidden UI and background levels give no signal`() {
        assertNull(TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertNull(TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertNull(TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertNull(TrimLevels.of(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
        assertNull(TrimLevels.of(0))
    }

    private fun pressure(
        avail: Long,
        previous: MemoryPressure = MemoryPressure.NORMAL,
        low: Boolean = false,
    ) = MemoryInfoPressure.of(avail * mb, 200 * mb, low, previous)

    @Test
    fun `available memory maps against the system threshold`() {
        assertEquals(MemoryPressure.NORMAL, pressure(401))
        assertEquals(MemoryPressure.MODERATE, pressure(400))
        assertEquals(MemoryPressure.MODERATE, pressure(201))
        assertEquals(MemoryPressure.CRITICAL, pressure(200))
        assertEquals(MemoryPressure.CRITICAL, pressure(50))
    }

    @Test
    fun `the platform low memory flag is critical whatever the numbers`() {
        assertEquals(MemoryPressure.CRITICAL, pressure(900, low = true))
    }

    @Test
    fun `a level is left only with a margin of available memory`() {
        assertEquals(MemoryPressure.CRITICAL, pressure(215, previous = MemoryPressure.CRITICAL))
        assertEquals(MemoryPressure.MODERATE, pressure(221, previous = MemoryPressure.CRITICAL))
        assertEquals(MemoryPressure.MODERATE, pressure(430, previous = MemoryPressure.MODERATE))
        assertEquals(MemoryPressure.NORMAL, pressure(441, previous = MemoryPressure.MODERATE))
    }

    @Test
    fun `an unknown threshold only reports the platform flag`() {
        assertEquals(
            MemoryPressure.NORMAL,
            MemoryInfoPressure.of(1, 0, lowMemory = false, previous = MemoryPressure.NORMAL),
        )
        assertEquals(
            MemoryPressure.CRITICAL,
            MemoryInfoPressure.of(1, 0, lowMemory = true, previous = MemoryPressure.NORMAL),
        )
    }

    @Test
    fun `a trim signal is held, then forgotten`() {
        val hold = TrimHold(holdNanos = 30)
        assertEquals(MemoryPressure.NORMAL, hold.current(0))
        hold.record(MemoryPressure.MODERATE, nowNanos = 100)
        assertEquals(MemoryPressure.MODERATE, hold.current(129))
        assertEquals(MemoryPressure.NORMAL, hold.current(130))
    }

    @Test
    fun `a lower signal does not cut a higher one short, a higher one replaces it`() {
        val hold = TrimHold(holdNanos = 30)
        hold.record(MemoryPressure.CRITICAL, nowNanos = 100)
        hold.record(MemoryPressure.MODERATE, nowNanos = 110)
        assertEquals(MemoryPressure.CRITICAL, hold.current(125))
        assertEquals(MemoryPressure.NORMAL, hold.current(130))
        hold.record(MemoryPressure.MODERATE, nowNanos = 200)
        hold.record(MemoryPressure.CRITICAL, nowNanos = 210)
        assertEquals(MemoryPressure.CRITICAL, hold.current(235))
    }

    @Test
    fun `a signal after the previous one lapsed starts a new hold`() {
        val hold = TrimHold(holdNanos = 30)
        hold.record(MemoryPressure.CRITICAL, nowNanos = 0)
        hold.record(MemoryPressure.MODERATE, nowNanos = 50)
        assertEquals(MemoryPressure.MODERATE, hold.current(60))
    }

    @Test
    fun `the worse of two pressures wins`() {
        assertEquals(MemoryPressure.CRITICAL, worseOf(MemoryPressure.NORMAL, MemoryPressure.CRITICAL))
        assertEquals(MemoryPressure.MODERATE, worseOf(MemoryPressure.MODERATE, MemoryPressure.NORMAL))
    }
}
