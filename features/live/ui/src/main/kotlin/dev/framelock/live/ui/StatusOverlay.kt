package dev.framelock.live.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.domain.pipeline.PipelineState
import java.util.Locale

private const val PULSE_LOW = 0.4f
private const val PULSE_MILLIS = 900
private const val COLOR_MILLIS = 200
private val DotSize = 10.dp

/** What the compact figures at the end of the top bar show. */
private class Readout(
    live: LiveMetrics,
) {
    val fps = String.format(Locale.ROOT, "%.0f", live.inferenceFps)
    val ms = (live.e2eMs ?: live.inferenceMs)?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "–"
}

/**
 * The top glass bar: the state as a filled chip, readable at a glance (docs/architecture.md), the model version, and,
 * while the HUD is off and frames are being analysed, the rate and latency. Under it, the reason a start did not go
 * through.
 */
@Composable
internal fun StatusOverlay(
    ui: PipelineUiState,
    startResult: StartResult?,
    live: LiveMetrics,
    showReadout: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar(ui, live, showReadout)
        StartMessage(startResult)
    }
}

@Composable
private fun TopBar(
    ui: PipelineUiState,
    live: LiveMetrics,
    showReadout: Boolean,
) {
    val analysing = ui.state is PipelineState.Running || ui.state is PipelineState.Degraded
    val readout = if (showReadout && analysing) remember(live) { Readout(live) } else null
    val numbers = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_NUMBERS)
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(ui.state)
            Box(Modifier.weight(1f)) {
                ui.model?.let {
                    Text(
                        stringResource(R.string.model_version, it.value),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            readout?.let {
                Text(
                    stringResource(R.string.readout, it.fps, it.ms),
                    color = TextPrimary,
                    style = numbers,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A filled pill in the state's colour with dark text, and a pulsing dot while running. */
@Composable
private fun StatusChip(state: PipelineState) {
    val (text, argb) = bannerOf(state)
    val fill by animateColorAsState(Color(argb), tween(COLOR_MILLIS), label = "state colour")
    val onState = Color(StateColors.ON_STATE)
    Row(
        Modifier.clip(CircleShape).background(fill).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state is PipelineState.Running) PulsingDot(onState)
        Text(
            text,
            color = onState,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        PULSE_LOW,
        1f,
        infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "pulse alpha",
    )
    Box(
        Modifier
            .size(DotSize)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape),
    )
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
    GlassSurface(Modifier.fillMaxWidth()) {
        val tint = if (error) Color(GlassPalette.DANGER_FILL) else Color.Transparent
        Text(
            text,
            Modifier.fillMaxWidth().background(tint).padding(horizontal = 20.dp, vertical = 16.dp),
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Colours are [StateColors], chosen for WCAG AA contrast with the dark chip text. */
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
        LiveMetrics.EMPTY,
        showReadout = true,
    )
}
