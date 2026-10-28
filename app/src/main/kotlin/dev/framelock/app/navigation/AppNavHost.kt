package dev.framelock.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.framelock.app.navigation.routes.liveRoutes
import dev.framelock.app.navigation.routes.modelsRoutes
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.telemetry.application.TrackScreen
import org.koin.compose.koinInject

@Composable
internal fun AppNavHost(
    owner: ViewModelStoreOwner,
    initialHud: Boolean,
) {
    val navigator: AppNavigator = koinInject()
    val trackScreen: TrackScreen = koinInject()
    val controller = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val entry by controller.currentBackStackEntryAsState()
    val screen = entry?.destination?.let { if (it.hasRoute<AppRoutes.Models>()) ScreenId.MODELS else ScreenId.LIVE }
    LaunchedEffect(screen) { if (screen != null) trackScreen(screen) }
    LaunchedEffect(controller, navigator, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navigator.commands.collect { route ->
                when (route) {
                    AppRoutes.Live -> {
                        controller.navigate(AppRoutes.Live) {
                            popUpTo<AppRoutes.Live>()
                            launchSingleTop =
                                true
                        }
                    }

                    AppRoutes.Models -> {
                        controller.navigate(AppRoutes.Models) { launchSingleTop = true }
                    }
                }
            }
        }
    }
    NavHost(
        controller,
        startDestination = AppRoutes.Live,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        liveRoutes(navigator, owner, initialHud)
        modelsRoutes(navigator, owner)
    }
}
