package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.Spacing
import dev.framelock.live.ui.ControlsActions
import dev.framelock.live.ui.ControlsState

/** Portrait: a glass dock floating over the bottom of the video, 12 dp from the edges and above the navigation inset. */
@Composable
internal fun ControlsBar(
    state: ControlsState,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(Spacing.Medium),
    ) {
        Column(Modifier.padding(Spacing.Standard), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            StartStopButton(state.running, actions.pipeline, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourcePicker(state.source, actions.source, Modifier.weight(1f))
                HudToggle(state.hudVisible, actions.onToggleHud, actions.onTrack)
                ModelsButton(actions.onOpenModels)
            }
        }
    }
}
