package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.Spacing
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.live.ui.R
import dev.framelock.designsystem.Size as ComponentSize

/** A glass card of labelled rows in sections; values in tabular figures so digits do not jump around. */
@Composable
internal fun Hud(
    live: LiveMetrics,
    sourceDescription: String?,
    backend: String?,
    thermal: ThermalLevel,
    memory: MemoryPressure,
    modifier: Modifier = Modifier,
) {
    val values = remember(live, thermal, memory) { HudValues(live, thermal.name, memory.name) }
    AppCard(modifier.widthIn(min = ComponentSize.HudMin, max = ComponentSize.HudMax)) {
        Column(
            Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Standard),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            // The source line changes only when a session starts.
            sourceDescription?.let {
                HudRow(R.string.hud_source, it)
                Separator()
            }
            // Like the source line, it changes only when a model is loaded.
            backend?.let { HudRow(R.string.hud_backend, it) }
            HudRow(R.string.hud_input_fps, values.inputFps)
            HudRow(R.string.hud_inference_fps, values.inferenceFps)
            Separator()
            HudRow(R.string.hud_inference_ms, values.inferenceMs)
            HudRow(R.string.hud_e2e_ms, values.e2eMs)
            HudRow(R.string.hud_drops, values.drops)
            Separator()
            HudRow(R.string.hud_thermal, values.thermal)
            HudRow(R.string.hud_memory, values.memory)
        }
    }
}
