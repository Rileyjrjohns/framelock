package dev.framelock.live.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.domain.pipeline.InferenceRate
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.live.ui.ControlsState
import dev.framelock.live.ui.HudState
import dev.framelock.live.ui.LiveUiState
import dev.framelock.live.ui.LiveVideoState
import dev.framelock.live.ui.R
import dev.framelock.live.ui.SourceOption
import dev.framelock.live.ui.SourcePickerState

@Composable
internal fun previewLiveState(
    running: Boolean = false,
    menu: Boolean = false,
): LiveUiState {
    val source = SourceOption("auto", stringResource(R.string.live_preview_source))
    val pipeline = if (running) PipelineState.Running(InferenceRate.UNLIMITED) else PipelineState.Idle
    val hud =
        if (running) {
            HudState(
                source.label,
                stringResource(R.string.live_preview_backend),
                ThermalLevel.NONE,
                MemoryPressure.NORMAL,
            )
        } else {
            null
        }
    return LiveUiState(
        LiveVideoState(PipelineUiState(pipeline, null, null), LiveMetrics.EMPTY, hud = hud),
        ControlsState(running, running, SourcePickerState(source, listOf(source), enabled = !running, expanded = menu)),
    )
}
