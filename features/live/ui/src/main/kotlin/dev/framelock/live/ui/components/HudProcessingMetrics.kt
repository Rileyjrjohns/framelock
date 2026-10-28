package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.Spacing
import dev.framelock.live.ui.R

@Composable
internal fun HudProcessingMetrics(
    values: HudValues,
    thermal: String,
    memory: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        HudRow(R.string.live_main_hud_inference_ms, values.inferenceMs)
        HudRow(R.string.live_main_hud_e2e_ms, values.e2eMs)
        HudRow(R.string.live_main_hud_drops, values.drops)
        Separator()
        HudRow(R.string.live_main_hud_thermal, thermal)
        HudRow(R.string.live_main_hud_memory, memory)
    }
}
