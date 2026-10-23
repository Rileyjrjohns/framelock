package dev.framelock.core.domain.policy

import dev.framelock.core.domain.pipeline.DegradationCause
import dev.framelock.core.domain.pipeline.InferenceRate
import dev.framelock.core.domain.pipeline.PauseCause
import dev.framelock.core.domain.system.MemoryPressure
import dev.framelock.core.domain.system.ThermalLevel

/** What the pipeline does under the current thermal and memory conditions (ADR-013). */
sealed interface RateDecision {
    /** Keep inferring at [rate]. [cause] is null when nothing limits the rate. */
    data class Run(
        val rate: InferenceRate,
        val cause: DegradationCause?,
    ) : RateDecision

    data class Pause(
        val cause: PauseCause,
    ) : RateDecision
}

/** Maps `(ThermalLevel, MemoryPressure)` to a rate or a pause; the most restrictive of the two wins (ADR-013). */
object InferenceRatePolicy {
    private const val MODERATE_FPS = 15
    private const val SEVERE_FPS = 10
    private const val CRITICAL_FPS = 5
    private const val PAUSED = 0
    private val NO_LIMIT = InferenceRate.UNLIMITED.maxFps

    fun decide(
        thermal: ThermalLevel,
        memory: MemoryPressure,
    ): RateDecision {
        val thermalFps = thermal.allowedFps()
        val memoryFps = memory.allowedFps()
        val allowedFps = minOf(thermalFps, memoryFps)
        // A source names itself as the cause only when it imposes the limit that applies.
        val thermalLimits = thermalFps == allowedFps && thermalFps != NO_LIMIT
        val memoryLimits = memoryFps == allowedFps && memoryFps != NO_LIMIT
        return when (allowedFps) {
            PAUSED -> RateDecision.Pause(pauseCause(thermalLimits, memoryLimits))
            NO_LIMIT -> RateDecision.Run(InferenceRate.UNLIMITED, cause = null)
            else -> RateDecision.Run(InferenceRate.capped(allowedFps), degradationCause(thermalLimits, memoryLimits))
        }
    }

    private fun ThermalLevel.allowedFps(): Int =
        when (this) {
            ThermalLevel.NONE, ThermalLevel.LIGHT -> NO_LIMIT
            ThermalLevel.MODERATE -> MODERATE_FPS
            ThermalLevel.SEVERE -> SEVERE_FPS
            ThermalLevel.CRITICAL -> CRITICAL_FPS
            ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN -> PAUSED
        }

    private fun MemoryPressure.allowedFps(): Int =
        when (this) {
            MemoryPressure.NORMAL -> NO_LIMIT
            MemoryPressure.MODERATE -> MODERATE_FPS
            MemoryPressure.CRITICAL -> PAUSED
        }

    private fun pauseCause(
        thermalLimits: Boolean,
        memoryLimits: Boolean,
    ): PauseCause =
        when {
            thermalLimits && memoryLimits -> PauseCause.THERMAL_AND_MEMORY
            thermalLimits -> PauseCause.THERMAL
            else -> PauseCause.MEMORY
        }

    private fun degradationCause(
        thermalLimits: Boolean,
        memoryLimits: Boolean,
    ): DegradationCause =
        when {
            thermalLimits && memoryLimits -> DegradationCause.THERMAL_AND_MEMORY
            thermalLimits -> DegradationCause.THERMAL
            else -> DegradationCause.MEMORY
        }
}
