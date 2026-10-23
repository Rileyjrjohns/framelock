package dev.framelock.live.application.usecase

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.port.DeviceInfoProvider
import dev.framelock.live.domain.telemetry.LiveTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

sealed interface StartResult {
    data object Started : StartResult

    data object AlreadyRunning : StartResult

    data object NoActiveModel : StartResult

    data class LoadFailed(
        val cause: Throwable,
    ) : StartResult
}

/**
 * Starts a session on the active model. The model comes from [activeModel], wired in `:app` to the models feature:
 * live is upstream of models and never depends on it (ADR-014). Telemetry goes through [record], likewise.
 */
class StartPipeline(
    private val pipeline: InferencePipeline,
    private val activeModel: () -> ModelArtifact?,
    private val deviceInfo: DeviceInfoProvider,
    private val record: (TelemetryEvent) -> Unit,
) {
    suspend operator fun invoke(): StartResult {
        val model = activeModel()
        return when {
            pipeline.state.value.state != PipelineState.Idle -> StartResult.AlreadyRunning
            model == null -> StartResult.NoActiveModel
            else -> start(model)
        }
    }

    private suspend fun start(model: ModelArtifact): StartResult {
        val failure =
            try {
                pipeline.start(model)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (
                // The engine may fail to load in any way; the session simply does not start.
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                e
            }
        if (failure != null) return StartResult.LoadFailed(failure)
        val snapshot = deviceInfo.snapshot()
        record(
            LiveTelemetry.DeviceSnapshotRecorded(
                snapshot.model,
                snapshot.osVersion,
                snapshot.batteryPct,
                snapshot.batteryTempC,
                snapshot.thermalLevel,
            ),
        )
        return StartResult.Started
    }
}

class StopPipeline(
    private val pipeline: InferencePipeline,
) {
    suspend operator fun invoke() = pipeline.stop()
}

/** The live screen's inputs: the pipeline state (with only displayable results, ADR-001) and the HUD metrics. */
class ObservePipeline(
    pipeline: InferencePipeline,
    metrics: MetricsAggregator,
) {
    val state: StateFlow<PipelineUiState> = pipeline.state
    val live: StateFlow<LiveMetrics> = metrics.live
}

/** Called by the renderer when a frame is on screen, with `Clock.nowNanos()`: closes the e2e latency (ADR-011). */
class ReportFrameRendered(
    private val metrics: MetricsAggregator,
) {
    operator fun invoke(
        frameId: FrameId,
        sessionId: SessionId,
        renderNanos: Long,
    ) = metrics.onFrameRendered(frameId, sessionId, renderNanos)
}
