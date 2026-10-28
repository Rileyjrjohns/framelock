package dev.framelock.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** An outcome or error message on a glass card. */
@Composable
fun StatusMessage(
    text: String,
    error: Boolean,
    modifier: Modifier = Modifier,
    panel: Boolean = false,
) {
    AppCard(modifier, blur = !panel, elevated = !panel, fill = if (panel) Tint else null) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .background(if (error) DangerFill else AppColors.Transparent)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Standard),
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun StatusMessagePreview() {
    FramelockTheme { StatusMessage(stringResource(R.string.designsystem_catalog_message), error = true) }
}
