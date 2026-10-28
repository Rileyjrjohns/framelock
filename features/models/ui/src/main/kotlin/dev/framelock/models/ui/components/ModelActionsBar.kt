package dev.framelock.models.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.GhostButton
import dev.framelock.designsystem.PrimaryButton
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TouchTarget
import dev.framelock.models.ui.R
import dev.framelock.designsystem.R as DesignR

/** Roll back (glass) and import (gradient), always in reach in a dock at the bottom. */
@Composable
internal fun ModelActionsBar(
    enabled: Boolean,
    canRollBack: Boolean,
    onRollback: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(Spacing.Medium),
    ) {
        Row(Modifier.padding(Spacing.Standard), horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            GhostButton(
                onClick = onRollback,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled && canRollBack,
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_undo),
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.ExtraLarge),
                )
                Text(
                    stringResource(R.string.models_main_action_rollback),
                    Modifier.padding(start = Spacing.Compact),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            PrimaryButton(
                onClick = onImport,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled,
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_upload_file),
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.ExtraLarge),
                )
                Text(
                    stringResource(R.string.models_main_action_import),
                    Modifier.padding(start = Spacing.Compact),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
