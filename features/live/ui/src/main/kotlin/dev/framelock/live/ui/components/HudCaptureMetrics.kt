package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.Spacing
import dev.framelock.live.ui.R

@Composable
internal fun HudCaptureMetrics(
    values: HudValues,
    source: String?,
    backend: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        source?.let {
            HudRow(R.string.live_main_hud_source, it)
            Separator()
        }
        backend?.let { HudRow(R.string.live_main_hud_backend, it) }
        HudRow(R.string.live_main_hud_input_fps, values.inputFps)
        HudRow(R.string.live_main_hud_inference_fps, values.inferenceFps)
    }
}
