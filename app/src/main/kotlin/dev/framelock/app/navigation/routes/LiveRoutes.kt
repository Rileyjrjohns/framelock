package dev.framelock.app.navigation.routes

import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.framelock.app.CameraPermissionGate
import dev.framelock.app.navigation.AppNavigator
import dev.framelock.app.navigation.AppRoutes
import dev.framelock.live.ui.LiveScreen
import dev.framelock.live.ui.LiveViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun NavGraphBuilder.liveRoutes(
    navigator: AppNavigator,
    owner: ViewModelStoreOwner,
    initialHud: Boolean,
) {
    composable<AppRoutes.Live> {
        val viewModel: LiveViewModel =
            koinViewModel(viewModelStoreOwner = owner, parameters = { parametersOf(initialHud) })
        CameraPermissionGate { LiveScreen(viewModel, onOpenModels = { navigator.go(AppRoutes.Models) }) }
    }
}
