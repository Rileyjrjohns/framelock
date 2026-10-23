package dev.framelock.live.application.usecase

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.pipeline.SessionStart
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId
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
        val result = startSession()
        if (result == StartResult.Started) recordDeviceSnapshot()
        return result
    }

    /** The active model is read under the pipeline's lifecycle lock: a swap cannot slip in (ADR-003). */
    private suspend fun startSession(): StartResult =
        try {
            when (pipeline.start(activeModel)) {
                SessionStart.STARTED -> StartResult.Started
                SessionStart.ALREADY_RUNNING -> StartResult.AlreadyRunning
                SessionStart.NO_MODEL -> StartResult.NoActiveModel
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            // The engine may fail to load in any way.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            StartResult.LoadFailed(e)
        } catch (e: LinkageError) {
            // A native runtime that fails to link must not crash the app (rule 5).
            StartResult.LoadFailed(e)
        }

    /** Once per started session (ADR-004). Telemetry never fails a start. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun recordDeviceSnapshot() {
        try {
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
        } catch (e: Exception) {
            // The session runs; the snapshot is only telemetry.
        }
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
