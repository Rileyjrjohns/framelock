package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import dev.framelock.designsystem.StatusChip
import dev.framelock.live.domain.pipeline.PipelineState

/** A filled pill in the state's colour with dark text, and a pulsing dot while running. */
@Composable
internal fun PipelineStatusChip(state: PipelineState) {
    val (text, argb) = bannerOf(state)
    StatusChip(text, argb, running = state is PipelineState.Running)
}
