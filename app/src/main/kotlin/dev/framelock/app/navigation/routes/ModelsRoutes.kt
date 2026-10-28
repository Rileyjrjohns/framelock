package dev.framelock.app.navigation.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.framelock.app.navigation.AppNavigator
import dev.framelock.app.navigation.AppRoutes
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.models.ui.ModelsScreen
import dev.framelock.models.ui.ModelsScreenActions
import dev.framelock.models.ui.ModelsViewModel
import dev.framelock.models.ui.UriModelSource
import org.koin.androidx.compose.koinViewModel

internal fun NavGraphBuilder.modelsRoutes(
    navigator: AppNavigator,
    owner: ViewModelStoreOwner,
) {
    composable<AppRoutes.Models> {
        val vm: ModelsViewModel = koinViewModel(viewModelStoreOwner = owner)
        val ui by vm.state.collectAsStateWithLifecycle()
        val canSwap by vm.canSwap.collectAsStateWithLifecycle()
        val resolver = LocalContext.current.contentResolver
        val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.import(UriModelSource(resolver, uri))
            }
        ModelsScreen(
            ui.copy(canSwap = canSwap),
            ModelsScreenActions(
                onBack = { navigator.go(AppRoutes.Live) },
                onActivate = {
                    vm.track(ElementId.ACTIVATE_MODEL)
                    vm.activate(it)
                },
                onRollback = {
                    vm.track(ElementId.ROLLBACK_MODEL)
                    vm.rollback()
                },
                onImport = {
                    vm.track(ElementId.IMPORT_MODEL)
                    picker.launch(PickerMimeTypes)
                },
            ),
        )
    }
}

// A signed model package has no registered media type.
private val PickerMimeTypes = arrayOf("*/*")
