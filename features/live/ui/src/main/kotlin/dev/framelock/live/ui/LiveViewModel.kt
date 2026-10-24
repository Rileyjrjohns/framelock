package dev.framelock.live.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.DisplaySlot
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.ObservePipeline
import dev.framelock.live.application.usecase.ReportFrameRendered
import dev.framelock.live.application.usecase.StartPipeline
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.application.usecase.StopPipeline
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the live screen needs; everything else stays in the pipeline. */
class LiveViewModel(
    observe: ObservePipeline,
    private val start: StartPipeline,
    private val stop: StopPipeline,
    private val reportRendered: ReportFrameRendered,
    display: DisplaySlot,
    private val clock: Clock,
    tracer: Tracer,
    /** Label names of the active model, for the overlay. */
    val labelOf: (LabelId) -> String,
    /** Wired in `:app` to `TrackUiInteraction` (ADR-004). */
    val track: (ElementId) -> Unit,
    /** Lists the sources available right now; devices come and go, so it is asked each time the picker opens. */
    private val listSources: suspend () -> List<SourceOption>,
    /** Chooses the source by [SourceOption.id]; `:app` applies it at the next start. */
    private val chooseSource: (String) -> Unit,
    /** The current choice, `Auto` by default. */
    val selectedSource: StateFlow<SourceOption>,
    /** The active source and, for a capture device, its negotiated mode; null when stopped. UI text only. */
    val sourceDescription: StateFlow<String?>,
    /** The levels the pipeline degrades on, for the debug HUD (ADR-013, ADR-017). */
    val thermal: StateFlow<ThermalLevel>,
    val memory: StateFlow<MemoryPressure>,
    /** The inference backend in use (`GPU` or `CPU`), for the debug HUD; null until a model is loaded. UI text only. */
    val backend: StateFlow<String?>,
    /** Shows the HUD from the start (debug automation); the HUD button still toggles it. */
    hudVisibleAtStart: Boolean = false,
) : ViewModel() {
    val state: StateFlow<PipelineUiState> = observe.state
    val live: StateFlow<LiveMetrics> = observe.live

    private val hud = MutableStateFlow(hudVisibleAtStart)
    val hudVisible: StateFlow<Boolean> = hud.asStateFlow()

    private val sources = MutableStateFlow(listOf(selectedSource.value))

    /** Options for the picker, as of the last [refreshSources]. */
    val sourceOptions: StateFlow<List<SourceOption>> = sources.asStateFlow()

    private val lastStart = MutableStateFlow<StartResult?>(null)
    val startResult: StateFlow<StartResult?> = lastStart.asStateFlow()

    internal val renderer = FrameRenderer(display, tracer)
    private var lastReported: AnnotatedFrame? = null

    fun start() {
        viewModelScope.launch {
            val result = start.invoke()
            if (result is StartResult.LoadFailed) Log.e(TAG, "Model load failed", result.cause)
            lastStart.value = result
        }
    }

    fun stop() {
        viewModelScope.launch { stop.invoke() }
    }

    /** Lists the sources afresh; call when the picker opens. */
    fun refreshSources() {
        viewModelScope.launch { sources.value = listSources() }
    }

    /**
     * Chooses the source. The screen only offers it while the pipeline is idle, because the pipeline picks its source
     * when a session opens: a change applies at the next start.
     */
    fun selectSource(option: SourceOption) = chooseSource(option.id)

    fun toggleHud() {
        hud.value = !hud.value
    }

    /** Called once a frame is on screen: closes its end-to-end latency (ADR-011). */
    internal fun onDrawn(frame: AnnotatedFrame) {
        if (frame == lastReported) return
        lastReported = frame
        reportRendered(frame.frameId, frame.sessionId, clock.nowNanos())
    }

    private companion object {
        const val TAG = "LiveViewModel"
    }
}
