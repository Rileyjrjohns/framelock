package dev.framelock.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.framelock.designsystem.GlassPalette
import dev.framelock.live.ui.ControlsActions
import dev.framelock.live.ui.FrameCallbacks
import dev.framelock.live.ui.LiveUiState

@Composable
internal fun LiveLayout(
    state: LiveUiState,
    actions: ControlsActions,
    haze: HazeState,
    frames: FrameCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(GlassPalette.BACKGROUND))) {
        val landscape = maxWidth > maxHeight
        val video =
            @Composable { videoModifier: Modifier ->
                VideoArea(
                    state = state.video,
                    haze = haze,
                    landscape = landscape,
                    frames = frames,
                    modifier = videoModifier,
                )
            }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                video(Modifier.weight(1f))
                ControlsRail(state.controls, actions)
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                video(Modifier.fillMaxSize())
                ControlsBar(state.controls, actions, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
