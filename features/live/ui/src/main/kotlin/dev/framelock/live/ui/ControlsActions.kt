package dev.framelock.live.ui

import dev.framelock.kernel.telemetry.ElementId

class ControlsActions(
    val pipeline: PipelineActions,
    val source: SourceActions,
    val onToggleHud: () -> Unit,
    val onOpenModels: () -> Unit,
    val onTrack: (ElementId) -> Unit,
)
