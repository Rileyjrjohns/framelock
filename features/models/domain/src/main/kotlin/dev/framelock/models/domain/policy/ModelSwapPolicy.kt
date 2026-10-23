package dev.framelock.models.domain.policy

import dev.framelock.live.domain.pipeline.PipelineState

sealed interface SwapDecision {
    data object Allowed : SwapDecision

    data class Refused(
        val reason: SwapRefusal,
    ) : SwapDecision
}

enum class SwapRefusal {
    /** A capture session is in progress or starting; swapping would change results mid-session (ADR-003). */
    SESSION_ACTIVE,
}

/** A model swap is allowed only when the pipeline is `Idle` or `NoSignal`, never during a session (ADR-003). */
object ModelSwapPolicy {
    private val sessionActive = SwapDecision.Refused(SwapRefusal.SESSION_ACTIVE)

    // No `else`: a new PipelineState subtype must be classified here before the code compiles.
    fun decide(state: PipelineState): SwapDecision =
        when (state) {
            PipelineState.Idle -> SwapDecision.Allowed
            is PipelineState.NoSignal -> SwapDecision.Allowed
            PipelineState.Connecting -> sessionActive
            is PipelineState.Running -> sessionActive
            is PipelineState.Degraded -> sessionActive
            is PipelineState.Paused -> sessionActive
        }
}
