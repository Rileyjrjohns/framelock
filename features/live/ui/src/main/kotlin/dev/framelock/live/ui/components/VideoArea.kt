package dev.framelock.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import dev.chrisbanes.haze.HazeState
import dev.framelock.designsystem.AppColors
import dev.framelock.designsystem.IdleBrush
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.glassSource
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.ui.FrameCallbacks
import dev.framelock.live.ui.LiveVideoState
import dev.framelock.designsystem.Size as ComponentSize

/** The analysed frame with the status, start messages and HUD floating over it. */
@Composable
internal fun VideoArea(
    state: LiveVideoState,
    haze: HazeState,
    landscape: Boolean,
    frames: FrameCallbacks,
    modifier: Modifier = Modifier,
) {
    val ui = state.pipeline
    val live = state.metrics
    val startResult = state.startResult
    val hud = state.hud
    Box(modifier) {
        val annotated = ui.annotated
        val idle = annotated == null && ui.state == PipelineState.Idle
        // The matte around the frame is black whatever the theme: it is part of the video, not of the interface. This
        // layer is what the glass panels blur, so the panels themselves are siblings of it, never inside it.
        Box(
            Modifier
                .fillMaxSize()
                .glassSource(haze)
                .background(if (idle) IdleBrush else SolidColor(AppColors.Black)),
        ) {
            if (annotated != null) AnalysedFrame(annotated, frames.bitmapFor, frames.labelOf, frames.onDraw)
        }
        // Centred in the space under the top bar; on a short (landscape) screen the card drops its icon to fit.
        if (idle) {
            IdleHint(
                Modifier.align(Alignment.Center).padding(top = ComponentSize.LiveTopBarAllowance),
                compact = landscape,
            )
        }
        // The rail (landscape) or the dock (portrait) takes the insets on its own side.
        val sides =
            if (landscape) {
                WindowInsetsSides.Start + WindowInsetsSides.Top
            } else {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(sides)).padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            StatusOverlay(ui, startResult, live, showReadout = hud == null, Modifier.fillMaxWidth())
            hud?.let {
                Hud(
                    live,
                    it,
                    compact = landscape,
                    Modifier.align(Alignment.End),
                )
            }
        }
    }
}
