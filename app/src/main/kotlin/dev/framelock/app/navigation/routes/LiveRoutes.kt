package dev.framelock.app.navigation.routes

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.framelock.app.CameraPermissionGate
import dev.framelock.app.navigation.AppNavigator
import dev.framelock.app.navigation.AppRoutes
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.ui.ControlsActions
import dev.framelock.live.ui.ControlsState
import dev.framelock.live.ui.HudState
import dev.framelock.live.ui.LiveScreen
import dev.framelock.live.ui.LiveUiState
import dev.framelock.live.ui.LiveVideoState
import dev.framelock.live.ui.LiveViewModel
import dev.framelock.live.ui.PipelineActions
import dev.framelock.live.ui.SourceActions
import dev.framelock.live.ui.SourcePickerState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun NavGraphBuilder.liveRoutes(
    navigator: AppNavigator,
    owner: ViewModelStoreOwner,
    initialHud: Boolean,
) {
    composable<AppRoutes.Live> {
        val vm: LiveViewModel = koinViewModel(viewModelStoreOwner = owner, parameters = { parametersOf(initialHud) })
        val pipeline by vm.state.collectAsStateWithLifecycle()
        val metrics by vm.live.collectAsStateWithLifecycle()
        val hud by vm.hudVisible.collectAsStateWithLifecycle()
        val start by vm.startResult.collectAsStateWithLifecycle()
        val selected by vm.selectedSource.collectAsStateWithLifecycle()
        val options by vm.sourceOptions.collectAsStateWithLifecycle()
        val expanded by vm.sourceMenuExpanded.collectAsStateWithLifecycle()
        val description by vm.sourceDescription.collectAsStateWithLifecycle()
        val thermal by vm.thermal.collectAsStateWithLifecycle()
        val memory by vm.memory.collectAsStateWithLifecycle()
        val backend by vm.backend.collectAsStateWithLifecycle()
        val locales = LocalConfiguration.current.locales
        LaunchedEffect(locales) { vm.dismissSources() }
        val running = pipeline.state != PipelineState.Idle
        val state =
            LiveUiState(
                LiveVideoState(
                    pipeline,
                    metrics,
                    start,
                    if (hud && running) HudState(description, backend, thermal, memory) else null,
                ),
                ControlsState(
                    running,
                    hud,
                    SourcePickerState(selected, options, enabled = !running, expanded = expanded),
                ),
            )
        val actions =
            remember(vm, navigator) {
                ControlsActions(
                    PipelineActions(vm::start, vm::stop, vm.track),
                    SourceActions(vm::refreshSources, vm::selectSource, vm::dismissSources, vm.track),
                    vm::toggleHud,
                    { navigator.go(AppRoutes.Models) },
                    vm.track,
                )
            }
        CameraPermissionGate { LiveScreen(state, actions, vm.frames) }
    }
}
