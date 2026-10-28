package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.framelock.designsystem.AppDropdown
import dev.framelock.designsystem.GhostButton
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TouchTarget
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.ui.R
import dev.framelock.live.ui.SourceActions
import dev.framelock.live.ui.SourcePickerState
import dev.framelock.live.ui.track
import dev.framelock.designsystem.R as DesignR

/**
 * The source chip and its menu. Enabled only while the pipeline is stopped: the source is chosen when a session
 * opens, so a change made while running would only apply at the next start. The options are listed afresh each time
 * the menu opens.
 */
@Composable
internal fun SourcePicker(
    state: SourcePickerState,
    actions: SourceActions,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.live_main_action_source, state.selected.label)
    Box(modifier) {
        GhostButton(
            onClick = actions.onOpen,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget)
                    .semantics { contentDescription = description }
                    .track(ElementId.SELECT_SOURCE, actions.onTrack),
            enabled = state.enabled,
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_videocam),
                contentDescription = null,
                modifier = Modifier.size(Spacing.ExtraLarge),
            )
            Text(
                state.selected.label,
                Modifier.weight(1f).padding(horizontal = Spacing.Compact),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(DesignR.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(Spacing.ExtraLarge),
            )
        }
        AppDropdown(
            state.expanded,
            actions.onDismiss,
            state.options,
            { it.label },
            onSelect = actions.onSelect,
            itemModifier = Modifier.track(ElementId.CHOOSE_SOURCE, actions.onTrack),
        )
    }
}
