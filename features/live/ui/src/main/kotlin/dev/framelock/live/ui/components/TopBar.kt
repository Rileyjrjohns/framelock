package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TABULAR_NUMBERS
import dev.framelock.designsystem.TextPrimary
import dev.framelock.designsystem.TextSecondary
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.ui.R

@Composable
internal fun TopBar(
    ui: PipelineUiState,
    live: LiveMetrics,
    showReadout: Boolean,
) {
    val analysing = ui.state is PipelineState.Running || ui.state is PipelineState.Degraded
    val readout = if (showReadout && analysing) remember(live) { Readout(live) } else null
    val numbers = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_NUMBERS)
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.Standard, vertical = Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PipelineStatusChip(ui.state)
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
