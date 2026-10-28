package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.framelock.live.ui.HudState
import dev.framelock.live.ui.R
import dev.framelock.designsystem.Size as ComponentSize

/** A glass card of labelled rows in sections; values in tabular figures so digits do not jump around. */
@Composable
internal fun Hud(
    live: LiveMetrics,
    state: HudState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val thermalText = thermalLevelText(state.thermal)
    val memoryText = memoryPressureText(state.memory)
    val format = stringResource(R.string.live_main_number_decimal)
    val missing = stringResource(R.string.live_main_number_missing)
    val locale = LocalConfiguration.current.locales[0]
    val values =
        remember(live, locale, format, missing) {
            HudValues(live, format, missing, locale)
        }
    val width = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(ComponentSize.HudMin, ComponentSize.HudMax)
    val padding = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Standard)
    AppCard(modifier.then(width)) {
        // A short window needs two columns so thermal and memory remain visible without scrolling.
        if (compact) {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                HudCaptureMetrics(values, state.sourceDescription, state.backend, Modifier.weight(1f))
                HudProcessingMetrics(values, thermalText, memoryText, Modifier.weight(1f))
            }
        } else {
            Column(padding, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                HudCaptureMetrics(values, state.sourceDescription, state.backend)
                Separator()
                HudProcessingMetrics(values, thermalText, memoryText)
            }
        }
    }
}
