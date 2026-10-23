package dev.framelock.live.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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

/** A compact translucent card of labelled rows; values in a monospaced face so digits do not jump around. */
@Composable
internal fun Hud(
    live: LiveMetrics,
    sourceDescription: String?,
    thermal: ThermalLevel,
    memory: MemoryPressure,
    modifier: Modifier = Modifier,
) {
    val values = remember(live, thermal, memory) { HudValues(live, thermal.name, memory.name) }
    Surface(
        modifier = modifier.widthIn(min = 200.dp, max = 280.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The source line changes only when a session starts.
            sourceDescription?.let { HudRow(R.string.hud_source, it) }
            HudRow(R.string.hud_input_fps, values.inputFps)
            HudRow(R.string.hud_inference_fps, values.inferenceFps)
            HudRow(R.string.hud_inference_ms, values.inferenceMs)
            HudRow(R.string.hud_e2e_ms, values.e2eMs)
            HudRow(R.string.hud_drops, values.drops)
            HudRow(R.string.hud_thermal, values.thermal)
            HudRow(R.string.hud_memory, values.memory)
        }
    }
}

@Composable
private fun HudRow(
    label: Int,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
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
