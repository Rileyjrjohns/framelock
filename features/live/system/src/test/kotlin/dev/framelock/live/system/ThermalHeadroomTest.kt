package dev.framelock.live.system

import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ThermalHeadroomTest {
    private fun level(
        headroom: Float,
        previous: ThermalLevel = ThermalLevel.NONE,
    ) = HeadroomLevels.of(headroom, previous)

    @Test
    fun `forecast thresholds give the documented levels`() {
        assertEquals(ThermalLevel.NONE, level(0f))
        assertEquals(ThermalLevel.NONE, level(0.79f))
        assertEquals(ThermalLevel.LIGHT, level(0.80f))
        assertEquals(ThermalLevel.LIGHT, level(0.89f))
        assertEquals(ThermalLevel.MODERATE, level(0.90f))
        assertEquals(ThermalLevel.MODERATE, level(0.99f))
        assertEquals(ThermalLevel.SEVERE, level(1.0f))
    }

    @Test
    fun `a forecast above 1 never goes beyond SEVERE`() {
        assertEquals(ThermalLevel.SEVERE, level(3.5f))
    }

    @Test
    fun `NaN and infinite forecasts say nothing`() {
        assertNull(level(Float.NaN))
        assertNull(level(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `a level is left only below its threshold minus the release margin`() {
        assertEquals(ThermalLevel.MODERATE, level(0.88f, previous = ThermalLevel.MODERATE))
        assertEquals(ThermalLevel.LIGHT, level(0.84f, previous = ThermalLevel.MODERATE))
        assertEquals(ThermalLevel.SEVERE, level(0.96f, previous = ThermalLevel.SEVERE))
        assertEquals(ThermalLevel.MODERATE, level(0.94f, previous = ThermalLevel.SEVERE))
        assertEquals(ThermalLevel.NONE, level(0.5f, previous = ThermalLevel.SEVERE))
    }

    @Test
    fun `a rising forecast is taken at once`() {
        assertEquals(ThermalLevel.SEVERE, level(1.01f, previous = ThermalLevel.NONE))
    }

    @Test
    fun `the worse of two levels wins`() {
        assertEquals(ThermalLevel.SEVERE, worseOf(ThermalLevel.NONE, ThermalLevel.SEVERE))
        assertEquals(ThermalLevel.CRITICAL, worseOf(ThermalLevel.CRITICAL, ThermalLevel.MODERATE))
        assertEquals(ThermalLevel.NONE, worseOf(ThermalLevel.NONE, ThermalLevel.NONE))
    }

    @Test
    fun `polling emits NONE first, then only changes, once per interval`() =
        runTest {
            val readings = ArrayDeque(listOf(0.5f, 0.95f, 0.96f, Float.NaN, 1.2f, 0.2f))
            var reads = 0
            val seen = mutableListOf<ThermalLevel>()
            val job =
                backgroundScope.launch {
                    headroomLevels(2.seconds) {
                        reads++
                        readings.removeFirst()
                    }.toList(seen)
                }

            runCurrent()
            assertEquals(listOf(ThermalLevel.NONE), seen)
            assertEquals(0, reads)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(1, reads)
            advanceTimeBy(10.seconds)
            runCurrent()
            job.cancel()

            assertEquals(6, reads)
            assertEquals(listOf(ThermalLevel.NONE, ThermalLevel.MODERATE, ThermalLevel.SEVERE, ThermalLevel.NONE), seen)
        }

    @Test
    fun `a failing read is ignored and the level is kept`() =
        runTest {
            val seen = mutableListOf<ThermalLevel>()
            var call = 0
            val job =
                backgroundScope.launch {
                    headroomLevels(2.seconds) {
                        call++
                        if (call == 2) error("service gone") else 1.1f
                    }.toList(seen)
                }

            advanceTimeBy(7.seconds)
            runCurrent()
            job.cancel()

            assertEquals(listOf(ThermalLevel.NONE, ThermalLevel.SEVERE), seen)
            assertEquals(3, call)
        }
}
