package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
    val thermalText = thermalLevelText(thermal)
    val memoryText = memoryPressureText(memory)
    val format = stringResource(R.string.live_main_number_decimal)
    val missing = stringResource(R.string.live_main_number_missing)
    val locale = LocalConfiguration.current.locales[0]
    val values =
        remember(live, locale, format, missing) {
            HudValues(live, format, missing, locale)
        }
    AppCard(modifier.widthIn(min = ComponentSize.HudMin, max = ComponentSize.HudMax)) {
        Column(
            Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Standard),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            // The source line changes only when a session starts.
            sourceDescription?.let {
                HudRow(R.string.live_main_hud_source, it)
                Separator()
            }
            // Like the source line, it changes only when a model is loaded.
            backend?.let { HudRow(R.string.live_main_hud_backend, it) }
            HudRow(R.string.live_main_hud_input_fps, values.inputFps)
            HudRow(R.string.live_main_hud_inference_fps, values.inferenceFps)
            Separator()
            HudRow(R.string.live_main_hud_inference_ms, values.inferenceMs)
            HudRow(R.string.live_main_hud_e2e_ms, values.e2eMs)
            HudRow(R.string.live_main_hud_drops, values.drops)
            Separator()
            HudRow(R.string.live_main_hud_thermal, thermalText)
            HudRow(R.string.live_main_hud_memory, memoryText)
        }
    }
}
