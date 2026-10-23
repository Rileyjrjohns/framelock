package dev.framelock.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.framelock.kernel.telemetry.ElementId

/** Minimum touch target: usable with gloves and at a glance (docs/architecture.md, "UI"). */
private val TouchTarget = 64.dp

/** The primary action is larger still. */
private val PrimaryTarget = 72.dp

/** Width of the landscape rail. */
private val RailWidth = 264.dp

private const val SCRIM_ALPHA = 0.72f
private const val BAR_ALPHA = 0.9f

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

/** Portrait: a translucent bar over the bottom of the video, fading out upwards. */
@Composable
internal fun ControlsBar(
    state: ControlsState,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    val scrim = MaterialTheme.colorScheme.scrim
    val fade =
        remember(
            scrim,
        ) { Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(SCRIM_ALPHA), scrim.copy(BAR_ALPHA))) }
    Column(
        modifier
            .fillMaxWidth()
            .background(fade)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StartStopButton(state.running, actions, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourcePicker(state, actions, Modifier.weight(2f))
            HudToggle(state, actions, Modifier.weight(1f))
            ModelsButton(actions, Modifier.weight(1f))
        }
    }
}

/** Landscape: a rail beside the video (capture-card use is mostly landscape), scrollable on a short screen. */
@Composable
internal fun ControlsRail(
    state: ControlsState,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = BAR_ALPHA))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical),
            ).verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        StartStopButton(state.running, actions, Modifier.fillMaxWidth())
        SourcePicker(state, actions, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudToggle(state, actions, Modifier.weight(1f))
            ModelsButton(actions, Modifier.weight(1f))
        }
    }
}

/** Start or Stop: large, with distinct colours (the same green and red as the status they lead to). */
@Composable
private fun StartStopButton(
    running: Boolean,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    val argb = if (running) StateColors.FAULT else StateColors.RUNNING
    val label = stringResource(if (running) R.string.action_stop else R.string.action_start)
    val icon = if (running) R.drawable.ic_stop else R.drawable.ic_play_arrow
    Button(
        onClick = if (running) actions.onStop else actions.onStart,
        modifier =
            modifier
                .heightIn(min = PrimaryTarget)
                .track(if (running) ElementId.STOP else ElementId.START, actions.onTrack),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = Color(argb), contentColor = Color.White),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
        Text(label, Modifier.padding(start = 8.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The source button and its menu. Enabled only while the pipeline is stopped: the source is chosen when a session
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
        SecondaryButton(
            onClick = {
                actions.onOpenSources()
                expanded = true
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description }
                    .track(ElementId.SELECT_SOURCE, actions.onTrack),
            enabled = !state.running,
        ) {
            Icon(painterResource(R.drawable.ic_videocam), contentDescription = null)
            Text(
                state.selected.label,
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        expanded = false
                        actions.onSelectSource(option)
                    },
                    modifier = Modifier.heightIn(min = TouchTarget).track(ElementId.CHOOSE_SOURCE, actions.onTrack),
                )
            }
        }
    }
}

@Composable
private fun HudToggle(
    state: ControlsState,
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SecondaryButton(
        onClick = actions.onToggleHud,
        modifier = modifier.track(ElementId.TOGGLE_HUD, actions.onTrack),
        container = if (state.hudVisible) colors.primaryContainer else colors.surfaceContainerHighest,
        content = if (state.hudVisible) colors.onPrimaryContainer else colors.onSurface,
        stacked = true,
    ) {
        TileContent(R.drawable.ic_bug_report, stringResource(R.string.action_hud))
    }
}

@Composable
private fun ModelsButton(
    actions: ControlsActions,
    modifier: Modifier = Modifier,
) {
    SecondaryButton(actions.onOpenModels, modifier, stacked = true) {
        TileContent(R.drawable.ic_neurology, stringResource(R.string.action_models))
    }
}

/** An icon over a short label, for the narrow buttons. */
@Composable
private fun TileContent(
    icon: Int,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** The quieter buttons next to Start/Stop: tonal, translucent-friendly, and never below [TouchTarget]. */
@Composable
private fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    stacked: Boolean = false,
    label: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = TouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding =
            if (stacked) {
                PaddingValues(
                    horizontal = 4.dp,
                    vertical = 6.dp,
                )
            } else {
                PaddingValues(horizontal = 12.dp)
            },
        content = label,
    )
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

@Preview(widthDp = 264, heightDp = 360)
@Composable
private fun ControlsRailPreview() {
    val auto = SourceOption("auto", "Auto")
    ControlsRail(
        ControlsState(running = true, hudVisible = false, selected = auto, options = listOf(auto)),
        previewActions(),
    )
}

private fun previewActions() = ControlsActions({}, {}, {}, {}, {}, {}, {})
