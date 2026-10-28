package dev.framelock.live.ui.components

import dev.framelock.live.application.metrics.LiveMetrics
import java.util.Locale

/** The numbers of one HUD refresh, formatted once per [LiveMetrics] value rather than once per recomposition. */
internal class HudValues(
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
