package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.Spacing
import dev.framelock.live.ui.ControlsActions
import dev.framelock.live.ui.ControlsState
import dev.framelock.designsystem.Size as ComponentSize

/**
 * Landscape: a glass rail beside the video (capture-card use is mostly landscape), so it never covers part of the
 * analysed frame, scrollable on a short screen. Beside the video there is nothing to blur, so it is a flat fill.
 */
@Composable
internal fun ControlsRail(
    state: ControlsState,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
            .padding(Spacing.Medium)
            .width(ComponentSize.Rail),
        blur = false,
    ) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.Standard),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium, Alignment.CenterVertically),
        ) {
            StartStopButton(state.running, actions.pipeline, Modifier.fillMaxWidth())
            SourcePicker(state.source, actions.source, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium, Alignment.CenterHorizontally),
            ) {
                HudToggle(state.hudVisible, actions.onToggleHud, actions.onTrack)
                ModelsButton(actions.onOpenModels)
            }
        }
    }
}
