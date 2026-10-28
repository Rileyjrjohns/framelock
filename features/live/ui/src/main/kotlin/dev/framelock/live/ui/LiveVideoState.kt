package dev.framelock.live.ui

import androidx.compose.runtime.Immutable
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult

@Immutable
data class LiveVideoState(
    val pipeline: PipelineUiState,
    val metrics: LiveMetrics,
    val startResult: StartResult? = null,
    val hud: HudState? = null,
)
