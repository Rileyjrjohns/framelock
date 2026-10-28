package dev.framelock.live.ui

import androidx.compose.runtime.Immutable

/** What the controls show. */
@Immutable
data class ControlsState(
    val running: Boolean,
    val hudVisible: Boolean,
    val source: SourcePickerState,
)
