package dev.framelock.app

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.framelock.app.navigation.AppNavHost
import dev.framelock.designsystem.FramelockTheme
import dev.framelock.live.ui.LiveViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {
    private val environment: AppEnvironment by inject()
    private val debugHandler: DebugLaunchHandler by inject()

    // Debug builds only: a script drives a run through intent extras (see DebugLaunch). Release builds ignore them.
    private val debugLaunch by lazy {
        if (environment.debuggable) {
            DebugLaunch.parse(
                string = { intent.getStringExtra(it) },
                flag = { intent.getBooleanExtra(it, false) },
            )
        } else {
            DebugLaunch.NONE
        }
    }
    private val liveViewModel: LiveViewModel by viewModel { parametersOf(debugLaunch.hud) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark, transparent system bars: the video runs under them and the screens pad themselves by the insets.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: a recreation (rotation) must not start again what the user stopped.
        if (savedInstanceState == null) debugHandler.apply(debugLaunch) { liveViewModel.start() }
        // A scripted run must not lose its screen to the display timeout: nothing would be drawn.
        if (debugLaunch.isActive) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            FramelockTheme {
                Surface(Modifier.fillMaxSize()) { AppNavHost(this@MainActivity, debugLaunch.hud) }
            }
        }
    }
}
