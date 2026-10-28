package dev.framelock.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

/** An accessible indeterminate progress indicator. */
@Composable
fun AppLoader(
    description: String,
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier.size(Size.TouchTarget).semantics {
            contentDescription = description
        },
        color = AccentCyan,
    )
}

@Preview
@Composable
private fun AppLoaderPreview() {
    FramelockTheme { AppLoader(stringResource(R.string.designsystem_catalog_loading)) }
}
