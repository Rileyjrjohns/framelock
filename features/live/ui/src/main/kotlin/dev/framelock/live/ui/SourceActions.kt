package dev.framelock.live.ui

import dev.framelock.kernel.telemetry.ElementId

class SourceActions(
    val onOpen: () -> Unit,
    val onSelect: (SourceOption) -> Unit,
    val onDismiss: () -> Unit,
    val onTrack: (ElementId) -> Unit,
)
