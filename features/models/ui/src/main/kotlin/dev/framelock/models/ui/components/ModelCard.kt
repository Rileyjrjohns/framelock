package dev.framelock.models.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.PrimaryButton
import dev.framelock.designsystem.SelectionChip
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TextPrimary
import dev.framelock.designsystem.Tint
import dev.framelock.designsystem.TouchTarget
import dev.framelock.models.ui.ModelRow
import dev.framelock.models.ui.R
import dev.framelock.designsystem.R as DesignR

@Composable
internal fun ModelCard(
    row: ModelRow,
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), blur = false, elevated = false, fill = Tint) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TouchTarget).padding(Spacing.Large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Intermediate)) {
                Text(
                    stringResource(R.string.model_row_version, row.version.value),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (row.active || row.previous) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Compact)) {
                        if (row.active) {
                            SelectionChip(
                                stringResource(R.string.badge_active),
                                DesignR.drawable.ic_check_circle,
                                active = true,
                            )
                        }
                        if (row.previous) {
                            SelectionChip(
                                stringResource(R.string.badge_previous),
                                DesignR.drawable.ic_history,
                                active = false,
                            )
                        }
                    }
                }
            }
            if (!row.active) {
                PrimaryButton(
                    onActivate,
                    Modifier.heightIn(min = TouchTarget),
                    enabled = enabled,
                    contentPadding = Spacing.ExtraLarge,
                ) {
                    Text(stringResource(R.string.action_activate), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
