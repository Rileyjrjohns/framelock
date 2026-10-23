package dev.framelock.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.live.ui.LiveScreen
import dev.framelock.live.ui.LiveViewModel
import dev.framelock.live.ui.PermissionPrompt
import dev.framelock.models.ui.ModelsScreen
import dev.framelock.models.ui.ModelsViewModel

class MainActivity : ComponentActivity() {
    private val graph by lazy { (application as FramelockApplication).graph }
    private val liveViewModel: LiveViewModel by viewModels {
        viewModelFactory { initializer { graph.liveViewModel() } }
    }
    private val modelsViewModel: ModelsViewModel by viewModels {
        viewModelFactory { initializer { graph.modelsViewModel() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark, transparent system bars: the video runs under them and the screens pad themselves by the insets.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            FramelockTheme {
                Surface(Modifier.fillMaxSize()) { Navigation() }
            }
        }
    }

    /** Two screens, no navigation library: system back leaves Models for Live. */
    @Composable
    private fun Navigation() {
        var screen by rememberSaveable { mutableStateOf(ScreenId.LIVE) }
        LaunchedEffect(screen) { graph.trackScreen(screen) }
        BackHandler(enabled = screen == ScreenId.MODELS) { screen = ScreenId.LIVE }
        when (screen) {
            ScreenId.LIVE -> {
                CameraPermissionGate { LiveScreen(liveViewModel, onOpenModels = { screen = ScreenId.MODELS }) }
            }

            ScreenId.MODELS -> {
                ModelsScreen(modelsViewModel, onBack = { screen = ScreenId.LIVE })
            }
        }
    }

    /** CameraX is the M2 source: the live screen needs the camera permission first. */
    @Composable
    private fun CameraPermissionGate(content: @Composable () -> Unit) {
        val granted = {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        }
        var allowed by remember { mutableStateOf(granted()) }
        val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
        LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.CAMERA) }
        if (allowed) {
            content()
        } else {
            PermissionPrompt(
                message = stringResource(R.string.camera_permission_needed),
                actionLabel = stringResource(R.string.camera_permission_grant),
                onAction = { request.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}
