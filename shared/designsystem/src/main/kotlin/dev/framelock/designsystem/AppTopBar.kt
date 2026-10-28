package dev.framelock.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** A glass title bar with a full-size back action. */
@Composable
fun AppTopBar(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier) {
        Row(Modifier.padding(Spacing.Compact), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(onBack, R.drawable.ic_arrow_back, backDescription)
            Text(
                title,
                Modifier.padding(start = Spacing.Standard),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Preview
@Composable
private fun AppTopBarPreview() {
    FramelockTheme {
        AppTopBar(
            stringResource(R.string.designsystem_catalog_title),
            stringResource(R.string.designsystem_catalog_back),
            {},
        )
    }
}
