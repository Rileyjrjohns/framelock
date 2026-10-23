package dev.framelock.live.domain.pipeline

import dev.framelock.live.domain.frame.DisconnectReason

/** Maximum inference rate. [UNLIMITED]: one inference at a time, as fast as the engine goes (ADR-013). */
@JvmInline
value class InferenceRate private constructor(
    val maxFps: Int,
) {
    val isLimited: Boolean get() = maxFps != Int.MAX_VALUE

    /** Minimum time between two inference starts. */
    val minIntervalNanos: Long get() = if (isLimited) NANOS_PER_SECOND / maxFps else 0L

    override fun toString(): String = if (isLimited) "$maxFps fps" else "unlimited"

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        val UNLIMITED = InferenceRate(Int.MAX_VALUE)

        fun capped(maxFps: Int): InferenceRate {
            require(maxFps > 0) { "maxFps must be > 0, was $maxFps" }
            return InferenceRate(maxFps)
        }
    }
}

enum class DegradationCause { THERMAL, MEMORY, THERMAL_AND_MEMORY, ENGINE_ERROR }

enum class PauseCause { THERMAL, MEMORY, THERMAL_AND_MEMORY }

sealed interface PipelineState {
    data object Idle : PipelineState

    data object Connecting : PipelineState

    data class NoSignal(
        val reason: DisconnectReason,
    ) : PipelineState

    data class Running(
        val rate: InferenceRate,
    ) : PipelineState

    data class Degraded(
        val cause: DegradationCause,
        val rate: InferenceRate,
    ) : PipelineState

    data class Paused(
        val cause: PauseCause,
    ) : PipelineState
}
