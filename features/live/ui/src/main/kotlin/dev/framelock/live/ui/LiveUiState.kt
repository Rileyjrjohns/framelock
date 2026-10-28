package dev.framelock.live.ui

import androidx.compose.runtime.Immutable

@Immutable
data class LiveUiState(
    val video: LiveVideoState,
    val controls: ControlsState,
)
