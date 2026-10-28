package dev.framelock.models.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.framelock.designsystem.FramelockTheme
import dev.framelock.models.ui.ModelsScreen
import dev.framelock.models.ui.ModelsUiState

@Preview(widthDp = 420, heightDp = 800)
@Composable
internal fun ModelsEmptyPreview() {
    FramelockTheme { ModelsScreen(ModelsUiState(), PreviewActions) }
}
