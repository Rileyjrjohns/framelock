package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import dev.framelock.designsystem.Spacing

@Composable
internal fun RowScope.StartStopContent(
    icon: Int,
    label: String,
) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Spacing.Generous))
    Text(
        label,
        Modifier.padding(start = Spacing.Compact),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
