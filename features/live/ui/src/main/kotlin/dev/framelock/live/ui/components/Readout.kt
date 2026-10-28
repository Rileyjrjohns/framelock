package dev.framelock.live.ui.components

import dev.framelock.live.application.metrics.LiveMetrics
import java.util.Locale

/** What the compact figures at the end of the top bar show. */
internal class Readout(
    live: LiveMetrics,
) {
    val fps = String.format(Locale.ROOT, "%.0f", live.inferenceFps)
    val ms = (live.e2eMs ?: live.inferenceMs)?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "–"
}
