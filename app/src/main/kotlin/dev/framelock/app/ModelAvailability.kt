package dev.framelock.app

import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.models.domain.policy.ModelSwapPolicy
import dev.framelock.models.domain.policy.SwapDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class ModelAvailability(
    pipeline: InferencePipeline,
    scope: CoroutineScope,
) {
    val canSwap: StateFlow<Boolean> =
        pipeline.state
            .map(::swapAllowed)
            .stateIn(scope, SharingStarted.Eagerly, swapAllowed(pipeline.state.value))

    private fun swapAllowed(state: PipelineUiState): Boolean =
        ModelSwapPolicy.decide(state.state) == SwapDecision.Allowed
}
