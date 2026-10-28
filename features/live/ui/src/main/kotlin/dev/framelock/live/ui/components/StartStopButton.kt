package dev.framelock.live.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.DangerButton
import dev.framelock.designsystem.Motion
import dev.framelock.designsystem.PrimaryButton
import dev.framelock.designsystem.TouchTarget
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.ui.PipelineActions
import dev.framelock.live.ui.R
import dev.framelock.live.ui.track
import dev.framelock.designsystem.R as DesignR

/** Start (gradient) or Stop (red glass): the primary action, 64 dp high, crossfading when the state changes. */
@Composable
internal fun StartStopButton(
    running: Boolean,
    actions: PipelineActions,
    modifier: Modifier = Modifier,
) {
    Crossfade(running, modifier, animationSpec = tween(Motion.SWAP_MILLIS), label = "start stop") { isRunning ->
        val label = stringResource(if (isRunning) R.string.live_main_action_stop else R.string.live_main_action_start)
        val icon = if (isRunning) DesignR.drawable.ic_stop else DesignR.drawable.ic_play_arrow
        val buttonModifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .track(if (isRunning) ElementId.STOP else ElementId.START, actions.onTrack)
        if (isRunning) {
            DangerButton(actions.onStop, buttonModifier) { StartStopContent(icon, label) }
        } else {
            PrimaryButton(actions.onStart, buttonModifier) { StartStopContent(icon, label) }
        }
    }
}
