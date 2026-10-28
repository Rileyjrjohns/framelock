package dev.framelock.live.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.framelock.designsystem.FramelockTheme
import dev.framelock.live.ui.LiveScreen

@Preview(widthDp = 900, heightDp = 420)
@Composable
internal fun LiveLandscapePreview() {
    FramelockTheme { LiveScreen(previewLiveState(running = true), PreviewActions, PreviewFrames) }
}
