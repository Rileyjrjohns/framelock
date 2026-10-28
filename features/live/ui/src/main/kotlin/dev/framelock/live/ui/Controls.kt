package dev.framelock.live.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.AppDropdown
import dev.framelock.designsystem.AppIconButton
import dev.framelock.designsystem.DangerButton
import dev.framelock.designsystem.GhostButton
import dev.framelock.designsystem.PrimaryButton
import dev.framelock.designsystem.TouchTarget
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.designsystem.R as DesignR

/** Width of the landscape rail. */
private val RailWidth = 264.dp

private const val SWAP_MILLIS = 200

/** What the controls show. */
internal class ControlsState(
    val running: Boolean,
    val hudVisible: Boolean,
    val selected: SourceOption,
    val options: List<SourceOption>,
)

/** What the controls do. Opening a screen is navigation, not an interaction of the allowlist ([ElementId]). */
internal class ControlsActions(
    val onOpenSources: () -> Unit,
    val onSelectSource: (SourceOption) -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onToggleHud: () -> Unit,
    val onOpenModels: () -> Unit,
    val onTrack: (ElementId) -> Unit,
)

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
            .padding(12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StartStopButton(state.running, actions, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourcePicker(state, actions, Modifier.weight(1f))
                HudToggle(state, actions)
                ModelsButton(actions)
            }
        }
    }
}

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
            .padding(12.dp)
            .width(RailWidth),
        blur = false,
    ) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            StartStopButton(state.running, actions, Modifier.fillMaxWidth())
            SourcePicker(state, actions, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                HudToggle(state, actions)
                ModelsButton(actions)
            }
        }
    }
}

/** Start (gradient) or Stop (red glass): the primary action, 64 dp high, crossfading when the state changes. */
@Composable
private fun StartStopButton(
    running: Boolean,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    Crossfade(running, modifier, animationSpec = tween(SWAP_MILLIS), label = "start stop") { isRunning ->
        val label = stringResource(if (isRunning) R.string.action_stop else R.string.action_start)
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

/**
 * The source chip and its menu. Enabled only while the pipeline is stopped: the source is chosen when a session
 * opens, so a change made while running would only apply at the next start. The options are listed afresh each time
 * the menu opens.
 */
@Composable
private fun SourcePicker(
    state: ControlsState,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.action_source, state.selected.label)
    Box(modifier) {
        GhostButton(
            onClick = {
                actions.onOpenSources()
                expanded = true
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget)
                    .semantics { contentDescription = description }
                    .track(ElementId.SELECT_SOURCE, actions.onTrack),
            enabled = !state.running,
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_videocam),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                state.selected.label,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(DesignR.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        AppDropdown(
            expanded,
            { expanded = false },
            state.options,
            { it.label },
            onSelect = {
                expanded = false
                actions.onSelectSource(it)
            },
            itemModifier = Modifier.track(ElementId.CHOOSE_SOURCE, actions.onTrack),
        )
    }
}

@Composable
private fun HudToggle(
    state: ControlsState,
    actions: ControlsActions,
) {
    AppIconButton(
        icon = DesignR.drawable.ic_bug_report,
        description = stringResource(R.string.action_hud),
        onClick = actions.onToggleHud,
        modifier = Modifier.track(ElementId.TOGGLE_HUD, actions.onTrack),
        selected = state.hudVisible,
    )
}

@Composable
private fun ModelsButton(actions: ControlsActions) {
    AppIconButton(actions.onOpenModels, DesignR.drawable.ic_neurology, stringResource(R.string.action_models))
}

@Preview(widthDp = 400)
@Composable
private fun ControlsBarPreview() {
    val auto = SourceOption("auto", "Auto")
    ControlsBar(
        ControlsState(running = false, hudVisible = true, selected = auto, options = listOf(auto)),
        previewActions(),
    )
}

@Preview(widthDp = 288, heightDp = 400)
@Composable
private fun ControlsRailPreview() {
    val auto = SourceOption("auto", "Auto")
    ControlsRail(
        ControlsState(running = true, hudVisible = false, selected = auto, options = listOf(auto)),
        previewActions(),
    )
}

private fun previewActions() = ControlsActions({}, {}, {}, {}, {}, {}, {})

@Composable
private fun RowScope.StartStopContent(
    icon: Int,
    label: String,
) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
    Text(
        label,
        Modifier.padding(start = 8.dp),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
