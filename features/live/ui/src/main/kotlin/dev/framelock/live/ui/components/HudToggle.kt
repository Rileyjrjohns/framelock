package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.AppIconButton
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.ui.R
import dev.framelock.live.ui.track
import dev.framelock.designsystem.R as DesignR

@Composable
internal fun HudToggle(
    selected: Boolean,
    onToggle: () -> Unit,
    onTrack: (ElementId) -> Unit,
) {
    AppIconButton(
        icon = DesignR.drawable.ic_bug_report,
        description = stringResource(R.string.live_main_action_hud),
        onClick = onToggle,
        modifier = Modifier.track(ElementId.TOGGLE_HUD, onTrack),
        selected = selected,
    )
}
