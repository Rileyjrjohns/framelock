package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.Spacing
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        TopBar(ui, live, showReadout)
        StartMessage(startResult)
    }
}
