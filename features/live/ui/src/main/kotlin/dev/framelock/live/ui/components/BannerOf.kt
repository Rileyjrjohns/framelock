package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.StateColors
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.ui.R

/** Colours are [StateColors], chosen for WCAG AA contrast with the dark chip text. */
@Composable
internal fun bannerOf(state: PipelineState): Pair<String, Int> =
    when (state) {
        PipelineState.Idle -> {
            stringResource(R.string.state_idle) to StateColors.IDLE
        }

        PipelineState.Connecting -> {
            stringResource(R.string.state_connecting) to StateColors.CONNECTING
        }

        is PipelineState.Running -> {
            stringResource(R.string.state_running) to StateColors.RUNNING
        }

        is PipelineState.Degraded -> {
            stringResource(R.string.state_degraded, state.cause.label()) to
                StateColors.DEGRADED
        }

        is PipelineState.Paused -> {
            stringResource(R.string.state_paused, state.cause.label()) to StateColors.FAULT
        }

        is PipelineState.NoSignal -> {
            stringResource(R.string.state_no_signal) to StateColors.FAULT
        }
    }
