package dev.framelock.app.navigation.routes

import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.framelock.app.navigation.AppNavigator
import dev.framelock.app.navigation.AppRoutes
import dev.framelock.models.ui.ModelsScreen
import dev.framelock.models.ui.ModelsViewModel
import org.koin.androidx.compose.koinViewModel

internal fun NavGraphBuilder.modelsRoutes(
    navigator: AppNavigator,
    owner: ViewModelStoreOwner,
) {
    composable<AppRoutes.Models> {
        val viewModel: ModelsViewModel = koinViewModel(viewModelStoreOwner = owner)
        ModelsScreen(viewModel, onBack = { navigator.go(AppRoutes.Live) })
    }
}
