package dev.framelock.live.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.ObservePipeline
import dev.framelock.live.application.usecase.StartResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the live screen needs; everything else stays in the pipeline. */
class LiveViewModel(
    observe: ObservePipeline,
    private val commands: LiveCommands,
    signals: LiveSignals,
    private val rendering: LiveFrameRendering,
    hudVisibleAtStart: Boolean = false,
) : ViewModel() {
    val selectedSource = signals.selectedSource
    val sourceDescription = signals.sourceDescription
    val thermal = signals.thermal
    val memory = signals.memory
    val backend = signals.backend
    val track = commands::track
    val state: StateFlow<PipelineUiState> = observe.state
    val live: StateFlow<LiveMetrics> = observe.live

    private val hud = MutableStateFlow(hudVisibleAtStart)
    val hudVisible: StateFlow<Boolean> = hud.asStateFlow()

    private val sources = MutableStateFlow(listOf(selectedSource.value))

    /** Options for the picker, as of the last [refreshSources]. */
    val sourceOptions: StateFlow<List<SourceOption>> = sources.asStateFlow()

    private val lastStart = MutableStateFlow<StartResult?>(null)
    val startResult: StateFlow<StartResult?> = lastStart.asStateFlow()

    val frames = rendering.frames

    fun start() {
        viewModelScope.launch {
            val result = commands.start()
            if (result is StartResult.LoadFailed) Log.e(TAG, "Model load failed", result.cause)
            lastStart.value = result
        }
    }

    fun stop() {
        viewModelScope.launch { commands.stop() }
    }

    private val menu = MutableStateFlow(false)
    val sourceMenuExpanded: StateFlow<Boolean> = menu.asStateFlow()

    fun dismissSources() {
        menu.value = false
    }

    /** Lists the sources afresh; call when the picker opens. */
    fun refreshSources() {
        menu.value = true
        viewModelScope.launch { sources.value = commands.sources() }
    }

    /**
     * Chooses the source. The screen only offers it while the pipeline is idle, because the pipeline picks its source
     * when a session opens: a change applies at the next start.
     */
    fun selectSource(option: SourceOption) {
        menu.value = false
        commands.select(option.id)
    }

    fun toggleHud() {
        hud.value = !hud.value
    }

    private companion object {
        const val TAG = "LiveViewModel"
    }
}
