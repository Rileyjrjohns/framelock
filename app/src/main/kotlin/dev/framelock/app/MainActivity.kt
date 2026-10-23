package dev.framelock.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.live.ui.LiveScreen
import dev.framelock.live.ui.LiveViewModel
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
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.camera_permission_needed), fontSize = 22.sp)
                Button({ request.launch(Manifest.permission.CAMERA) }, Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.camera_permission_grant), fontSize = 20.sp)
                }
            }
        }
    }
}
