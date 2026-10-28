package dev.framelock.live.ui

import dev.framelock.kernel.telemetry.ElementId

class PipelineActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onTrack: (ElementId) -> Unit,
)
