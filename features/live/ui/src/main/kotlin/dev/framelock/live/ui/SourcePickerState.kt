package dev.framelock.live.ui

import androidx.compose.runtime.Immutable

@Immutable
data class SourcePickerState(
    val selected: SourceOption,
    val options: List<SourceOption>,
    val enabled: Boolean = true,
    val expanded: Boolean = false,
)
