package dev.framelock.live.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.framelock.kernel.port.Clock
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
    /** Label names of the active model, for the overlay. */
    val labelOf: (LabelId) -> String,
    /** Wired in `:app` to `TrackUiInteraction` (ADR-004). */
    val track: (ElementId) -> Unit,
) : ViewModel() {
    val state: StateFlow<PipelineUiState> = observe.state
    val live: StateFlow<LiveMetrics> = observe.live

    private val hud = MutableStateFlow(false)
    val hudVisible: StateFlow<Boolean> = hud.asStateFlow()

    private val lastStart = MutableStateFlow<StartResult?>(null)
    val startResult: StateFlow<StartResult?> = lastStart.asStateFlow()

    internal val renderer = FrameRenderer(display)
    private var lastReported: AnnotatedFrame? = null

    fun start() {
        viewModelScope.launch { lastStart.value = start.invoke() }
    }

    fun stop() {
        viewModelScope.launch { stop.invoke() }
    }

    fun toggleHud() {
        hud.value = !hud.value
    }

    /** Called once a frame is on screen: closes its end-to-end latency (ADR-011). */
    internal fun onDrawn(frame: AnnotatedFrame) {
        if (frame == lastReported) return
        lastReported = frame
        reportRendered(frame.frameId, frame.sessionId, clock.nowNanos())
    }
}
