package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TABULAR_NUMBERS
import dev.framelock.designsystem.TextPrimary
import dev.framelock.designsystem.TextSecondary

@Composable
internal fun HudRow(
    label: Int,
    value: String,
) {
    val numbers = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_NUMBERS)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            value,
            Modifier.weight(1f),
            color = TextPrimary,
            style = numbers,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
