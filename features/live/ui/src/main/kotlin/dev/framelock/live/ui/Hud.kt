package dev.framelock.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import java.util.Locale

/** The numbers of one HUD refresh, formatted once per [LiveMetrics] value rather than once per recomposition. */
private class HudValues(
    live: LiveMetrics,
    val thermal: String,
    val memory: String,
) {
    val inputFps = fixed(live.inputFps)
    val inferenceFps = fixed(live.inferenceFps)
    val inferenceMs = live.inferenceMs?.let(::fixed) ?: MISSING
    val e2eMs = live.e2eMs?.let(::fixed) ?: MISSING
    val drops = live.droppedFrames.toString()

    private fun fixed(value: Double) = String.format(Locale.ROOT, "%.1f", value)

    private companion object {
        const val MISSING = "–"
    }
}

/** A glass card of labelled rows in sections; values in tabular figures so digits do not jump around. */
@Composable
internal fun Hud(
    live: LiveMetrics,
    sourceDescription: String?,
    thermal: ThermalLevel,
    memory: MemoryPressure,
    modifier: Modifier = Modifier,
) {
    val values = remember(live, thermal, memory) { HudValues(live, thermal.name, memory.name) }
    GlassSurface(modifier.widthIn(min = 220.dp, max = 300.dp)) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The source line changes only when a session starts.
            sourceDescription?.let {
                HudRow(R.string.hud_source, it)
                Separator()
            }
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

@Composable
private fun Separator() {
    Spacer(
        Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(GlassPalette.BORDER)),
    )
}

@Composable
private fun HudRow(
    label: Int,
    value: String,
) {
    val numbers = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_NUMBERS)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            value,
            Modifier.weight(1f),
            color = TextPrimary,
            style = numbers,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun HudPreview() {
    Hud(
        LiveMetrics.EMPTY.copy(
            inputFps = 30.0,
            inferenceFps = 24.5,
            inferenceMs = 12.3,
            e2eMs = 48.0,
            droppedFrames = 7,
        ),
        sourceDescription = "Back camera",
        thermal = ThermalLevel.NONE,
        memory = MemoryPressure.NORMAL,
    )
}
