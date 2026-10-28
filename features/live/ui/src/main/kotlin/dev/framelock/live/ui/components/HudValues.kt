package dev.framelock.live.ui.components

import dev.framelock.live.application.metrics.LiveMetrics
import java.util.Locale

/** The numbers of one HUD refresh, formatted once per [LiveMetrics] value rather than once per recomposition. */
internal class HudValues(
    live: LiveMetrics,
    private val format: String,
    missing: String,
    private val locale: Locale,
) {
    val inputFps = fixed(live.inputFps)
    val inferenceFps = fixed(live.inferenceFps)
    val inferenceMs = live.inferenceMs?.let(::fixed) ?: missing
    val e2eMs = live.e2eMs?.let(::fixed) ?: missing
    val drops = live.droppedFrames.toString()

    private fun fixed(value: Double) = String.format(locale, format, value)
}
