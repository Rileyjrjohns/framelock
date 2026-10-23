package dev.framelock.live.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.domain.pipeline.PipelineState
import java.util.Locale

/** The state pill, and under it the reason a start did not go through. Readable at a glance (docs/architecture.md). */
@Composable
internal fun StatusOverlay(
    ui: PipelineUiState,
    startResult: StartResult?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(ui)
        StartMessage(startResult)
    }
}

@Composable
private fun StatusPill(ui: PipelineUiState) {
    val (text, color) = bannerOf(ui.state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(color),
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            ui.model?.let { Text(stringResource(R.string.model_version, it.value), fontSize = 16.sp) }
        }
    }
}

/** Why nothing is running: no model installed, or the active one did not load. */
@Composable
private fun StartMessage(startResult: StartResult?) {
    val (text, error) =
        when (startResult) {
            StartResult.NoActiveModel -> {
                stringResource(R.string.start_no_model) to false
            }

            is StartResult.LoadFailed -> {
                val reason = startResult.cause.message ?: startResult.cause::class.simpleName.orEmpty()
                stringResource(R.string.start_load_failed, reason) to true
            }

            else -> {
                return
            }
        }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (error) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (error) colors.onErrorContainer else colors.onSurface,
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

/** Colours are [StateColors], chosen for WCAG AA contrast with white text. */
@Composable
private fun bannerOf(state: PipelineState): Pair<String, Int> =
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

private fun Enum<*>.label(): String = name.lowercase(Locale.ROOT).replace('_', ' ')

@Preview(widthDp = 400)
@Composable
private fun StatusOverlayPreview() {
    StatusOverlay(
        PipelineUiState(PipelineState.Connecting, annotated = null, model = ModelVersion("1.0.0")),
        StartResult.NoActiveModel,
    )
}
