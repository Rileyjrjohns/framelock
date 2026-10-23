package dev.framelock.live.domain.policy

import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.domain.pipeline.InferenceRate
import dev.framelock.live.domain.pipeline.PauseCause
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import dev.framelock.live.domain.system.MemoryPressure.CRITICAL as CRITICAL_MEMORY
import dev.framelock.live.domain.system.MemoryPressure.MODERATE as MODERATE_MEMORY
import dev.framelock.live.domain.system.MemoryPressure.NORMAL as NORMAL_MEMORY

/** The table of ADR-013 is the specification: [matrix] spells out every row, the other tests check its shape. */
class InferenceRatePolicyTest {
    @ParameterizedTest
    @MethodSource("matrix")
    fun `matrix of the 21 rows`(row: RateRow) {
        assertEquals(row.expected, InferenceRatePolicy.decide(row.thermal, row.memory))
    }

    @Test
    fun `table covers every ThermalLevel and MemoryPressure`() {
        val everyPair = ThermalLevel.entries.flatMap { thermal -> MemoryPressure.entries.map { thermal to it } }

        assertEquals(ThermalLevel.entries.size * MemoryPressure.entries.size, matrix().size)
        assertEquals(everyPair.toSet(), matrix().map { it.thermal to it.memory }.toSet())
    }

    @Test
    fun `rate never increases as thermal worsens`() {
        MemoryPressure.entries.forEach { memory ->
            val allowed = ThermalLevel.entries.map { allowedFps(InferenceRatePolicy.decide(it, memory)) }
            assertEquals(allowed.sortedDescending(), allowed, "memory $memory: $allowed")
        }
    }

    @Test
    fun `rate never increases as memory worsens`() {
        ThermalLevel.entries.forEach { thermal ->
            val allowed = MemoryPressure.entries.map { allowedFps(InferenceRatePolicy.decide(thermal, it)) }
            assertEquals(allowed.sortedDescending(), allowed, "thermal $thermal: $allowed")
        }
    }

    @Test
    fun `EMERGENCY and SHUTDOWN pause at every memory level`() {
        listOf(ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN).forEach { thermal ->
            MemoryPressure.entries.forEach { memory ->
                val decision = InferenceRatePolicy.decide(thermal, memory)
                assertTrue(decision is RateDecision.Pause, "$thermal with $memory gave $decision")
            }
        }
    }

    @Test
    fun `CRITICAL memory pauses at every thermal level`() {
        ThermalLevel.entries.forEach { thermal ->
            val decision = InferenceRatePolicy.decide(thermal, CRITICAL_MEMORY)
            assertTrue(decision is RateDecision.Pause, "$thermal with CRITICAL memory gave $decision")
        }
    }

    @Test
    fun `NONE and NORMAL give the unlimited rate`() {
        val decision = InferenceRatePolicy.decide(ThermalLevel.NONE, NORMAL_MEMORY)

        assertEquals(RateDecision.Run(InferenceRate.UNLIMITED, cause = null), decision)
        assertFalse((decision as RateDecision.Run).rate.isLimited)
    }

    @Test
    fun `decision names the limiting cause - thermal, memory or both`() {
        // Thermal alone limits.
        assertEquals(
            cappedAt(15, DegradationCause.THERMAL),
            InferenceRatePolicy.decide(ThermalLevel.MODERATE, NORMAL_MEMORY),
        )
        // Memory alone limits.
        assertEquals(
            cappedAt(15, DegradationCause.MEMORY),
            InferenceRatePolicy.decide(ThermalLevel.NONE, MODERATE_MEMORY),
        )
        // Both impose the same cap.
        assertEquals(
            cappedAt(15, DegradationCause.THERMAL_AND_MEMORY),
            InferenceRatePolicy.decide(ThermalLevel.MODERATE, MODERATE_MEMORY),
        )
        // Both ask for a pause.
        assertEquals(
            RateDecision.Pause(PauseCause.THERMAL_AND_MEMORY),
            InferenceRatePolicy.decide(ThermalLevel.EMERGENCY, CRITICAL_MEMORY),
        )
        // Both limit, but thermal is stricter: memory is not the limiting cause.
        assertEquals(
            cappedAt(10, DegradationCause.THERMAL),
            InferenceRatePolicy.decide(ThermalLevel.SEVERE, MODERATE_MEMORY),
        )
        // Both limit, but memory is stricter (it pauses): thermal is not the limiting cause.
        assertEquals(
            RateDecision.Pause(PauseCause.MEMORY),
            InferenceRatePolicy.decide(ThermalLevel.SEVERE, CRITICAL_MEMORY),
        )
    }

    @Test
    fun `combined decision is the most restrictive of the two`() {
        ThermalLevel.entries.forEach { thermal ->
            MemoryPressure.entries.forEach { memory ->
                val thermalOnly = allowedFps(InferenceRatePolicy.decide(thermal, NORMAL_MEMORY))
                val memoryOnly = allowedFps(InferenceRatePolicy.decide(ThermalLevel.NONE, memory))
                val combined = allowedFps(InferenceRatePolicy.decide(thermal, memory))

                assertEquals(minOf(thermalOnly, memoryOnly), combined, "$thermal with $memory")
            }
        }
    }

    /** Frames per second a decision lets through: 0 for a pause, [Int.MAX_VALUE] for the unlimited rate. */
    private fun allowedFps(decision: RateDecision): Int =
        when (decision) {
            is RateDecision.Pause -> 0
            is RateDecision.Run -> decision.rate.maxFps
        }

    companion object {
        @JvmStatic
        fun matrix(): List<RateRow> = MATRIX
    }
}

data class RateRow(
    val thermal: ThermalLevel,
    val memory: MemoryPressure,
    val expected: RateDecision,
)

private fun cappedAt(
    fps: Int,
    cause: DegradationCause,
): RateDecision = RateDecision.Run(InferenceRate.capped(fps), cause)

private val UNLIMITED: RateDecision = RateDecision.Run(InferenceRate.UNLIMITED, cause = null)

private fun paused(cause: PauseCause): RateDecision = RateDecision.Pause(cause)

private val MATRIX: List<RateRow> =
    listOf(
        // NONE and LIGHT never limit by themselves.
        RateRow(ThermalLevel.NONE, NORMAL_MEMORY, UNLIMITED),
        RateRow(ThermalLevel.NONE, MODERATE_MEMORY, cappedAt(15, DegradationCause.MEMORY)),
        RateRow(ThermalLevel.NONE, CRITICAL_MEMORY, paused(PauseCause.MEMORY)),
        RateRow(ThermalLevel.LIGHT, NORMAL_MEMORY, UNLIMITED),
        RateRow(ThermalLevel.LIGHT, MODERATE_MEMORY, cappedAt(15, DegradationCause.MEMORY)),
        RateRow(ThermalLevel.LIGHT, CRITICAL_MEMORY, paused(PauseCause.MEMORY)),
        // MODERATE: 15 fps, the same cap as MODERATE memory.
        RateRow(ThermalLevel.MODERATE, NORMAL_MEMORY, cappedAt(15, DegradationCause.THERMAL)),
        RateRow(ThermalLevel.MODERATE, MODERATE_MEMORY, cappedAt(15, DegradationCause.THERMAL_AND_MEMORY)),
        RateRow(ThermalLevel.MODERATE, CRITICAL_MEMORY, paused(PauseCause.MEMORY)),
        // SEVERE: 10 fps, stricter than MODERATE memory.
        RateRow(ThermalLevel.SEVERE, NORMAL_MEMORY, cappedAt(10, DegradationCause.THERMAL)),
        RateRow(ThermalLevel.SEVERE, MODERATE_MEMORY, cappedAt(10, DegradationCause.THERMAL)),
        RateRow(ThermalLevel.SEVERE, CRITICAL_MEMORY, paused(PauseCause.MEMORY)),
        // CRITICAL: 5 fps, stricter than MODERATE memory.
        RateRow(ThermalLevel.CRITICAL, NORMAL_MEMORY, cappedAt(5, DegradationCause.THERMAL)),
        RateRow(ThermalLevel.CRITICAL, MODERATE_MEMORY, cappedAt(5, DegradationCause.THERMAL)),
        RateRow(ThermalLevel.CRITICAL, CRITICAL_MEMORY, paused(PauseCause.MEMORY)),
        // EMERGENCY and SHUTDOWN always pause.
        RateRow(ThermalLevel.EMERGENCY, NORMAL_MEMORY, paused(PauseCause.THERMAL)),
        RateRow(ThermalLevel.EMERGENCY, MODERATE_MEMORY, paused(PauseCause.THERMAL)),
        RateRow(ThermalLevel.EMERGENCY, CRITICAL_MEMORY, paused(PauseCause.THERMAL_AND_MEMORY)),
        RateRow(ThermalLevel.SHUTDOWN, NORMAL_MEMORY, paused(PauseCause.THERMAL)),
        RateRow(ThermalLevel.SHUTDOWN, MODERATE_MEMORY, paused(PauseCause.THERMAL)),
        RateRow(ThermalLevel.SHUTDOWN, CRITICAL_MEMORY, paused(PauseCause.THERMAL_AND_MEMORY)),
    )
