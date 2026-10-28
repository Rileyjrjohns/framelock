package dev.framelock.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** A selection badge with an accent edge for the active variant. */
@Composable
fun SelectionChip(
    text: String,
    icon: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    BaseChip(
        modifier =
            modifier
                .clip(CircleShape)
                .background(Tint)
                .border(
                    if (active) Size.SelectedEdge else Size.Edge,
                    if (active) AccentBrush else SolidColor(Edge),
                    CircleShape,
                ).padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        gap = Spacing.Small,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(Size.SmallIcon),
            tint = if (active) AccentCyan else TextSecondary,
        )
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
private fun SelectionChipPreview() {
    FramelockTheme {
        SelectionChip(
            stringResource(R.string.designsystem_catalog_title),
            R.drawable.ic_check_circle,
            true,
        )
    }
}
