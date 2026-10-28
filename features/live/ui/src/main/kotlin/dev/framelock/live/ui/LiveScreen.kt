package dev.framelock.live.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.rememberHazeState
import dev.framelock.designsystem.GlassBackdrop
import dev.framelock.live.ui.components.LiveLayout

/** Draws the analysed frame and its overlay together (ADR-001). */
@Composable
fun LiveScreen(
    state: LiveUiState,
    actions: ControlsActions,
    frames: FrameCallbacks,
    modifier: Modifier = Modifier,
) {
    val haze = rememberHazeState()
    GlassBackdrop(haze) { LiveLayout(state, actions, haze, frames, modifier) }
}
