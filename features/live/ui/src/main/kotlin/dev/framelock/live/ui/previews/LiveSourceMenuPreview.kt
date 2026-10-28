package dev.framelock.live.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.framelock.designsystem.FramelockTheme
import dev.framelock.live.ui.LiveScreen

@Preview(widthDp = 400, heightDp = 800)
@Composable
internal fun LiveSourceMenuPreview() {
    FramelockTheme { LiveScreen(previewLiveState(menu = true), PreviewActions, PreviewFrames) }
}
