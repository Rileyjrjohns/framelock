package dev.framelock.live.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.framelock.designsystem.FramelockTheme
import dev.framelock.live.ui.previews.PreviewActions
import dev.framelock.live.ui.previews.PreviewFrames
import dev.framelock.live.ui.previews.previewLiveState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HudLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyMetricRemainsVisibleInLandscape() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.setContent {
            FramelockTheme {
                LiveScreen(previewLiveState(running = true), PreviewActions, PreviewFrames)
            }
        }
        listOf(
            R.string.live_main_hud_source,
            R.string.live_main_hud_backend,
            R.string.live_main_hud_input_fps,
            R.string.live_main_hud_inference_fps,
            R.string.live_main_hud_inference_ms,
            R.string.live_main_hud_e2e_ms,
            R.string.live_main_hud_drops,
            R.string.live_main_hud_thermal,
            R.string.live_main_hud_memory,
        ).forEach { resource ->
            compose.onNodeWithText(compose.activity.getString(resource)).assertIsDisplayed()
        }
    }
}
