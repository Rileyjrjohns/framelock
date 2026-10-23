package dev.framelock.app

import android.content.Context
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.usecase.ObservePipeline
import dev.framelock.live.application.usecase.ReportFrameRendered
import dev.framelock.live.application.usecase.StartPipeline
import dev.framelock.live.application.usecase.StopPipeline
import dev.framelock.live.capture.camerax.CameraFacing
import dev.framelock.live.capture.camerax.CameraXCameras
import dev.framelock.live.capture.camerax.CameraXFrameSource
import dev.framelock.live.capture.uvc.UvcDevices
import dev.framelock.live.capture.uvc.UvcFrameSource
import dev.framelock.live.domain.frame.SessionIdSequence
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.live.inference.litert.LiteRtInferenceEngine
import dev.framelock.live.system.AndroidClock
import dev.framelock.live.system.AndroidDeviceInfoProvider
import dev.framelock.live.system.AndroidTracer
import dev.framelock.live.system.NotYetObservedMemoryPressure
import dev.framelock.live.system.PowerManagerThermalMonitor
import dev.framelock.live.ui.LiveViewModel
import dev.framelock.telemetry.application.TelemetryRecorder
import dev.framelock.telemetry.application.TrackScreen
import dev.framelock.telemetry.application.TrackUiInteraction
import dev.framelock.telemetry.room.LogcatTelemetrySink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Composition root (manual DI): the only place that knows every module. Features never see each other's adapters;
 * cross-feature links (active model, telemetry) are plain functions wired here (ADR-014).
 */
class AppGraph(
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = AndroidClock()
    private val tracer = AndroidTracer()

    private val recorder = TelemetryRecorder(LogcatTelemetrySink())
    private val record: (TelemetryEvent) -> Unit = recorder::record
    private val trackUi = TrackUiInteraction(recorder)
    val trackScreen = TrackScreen(recorder)

    private val developmentModel = DevelopmentModel(context.applicationContext)
    private val metrics = MetricsAggregator(clock, record)

    // One sequence for every source: the pipeline refuses a session id that does not increase (ADR-016).
    private val sessionIds = SessionIdSequence()
    private val source =
        SelectingFrameSource(
            external =
                CameraXFrameSource(
                    context.applicationContext,
                    clock,
                    tracer,
                    sessionIds,
                    facing = CameraFacing.EXTERNAL,
                ),
            uvc = UvcFrameSource(context.applicationContext, clock, tracer, sessionIds),
            back = CameraXFrameSource(context.applicationContext, clock, tracer, sessionIds),
            hasExternalCamera = { CameraXCameras.hasExternal(context.applicationContext) },
            hasUvcDevice = { UvcDevices.isAttached(context.applicationContext) },
        )
    private val pipeline =
        InferencePipeline(
            source = source,
            engine = LiteRtInferenceEngine(tracer),
            thermal = PowerManagerThermalMonitor(context.applicationContext),
            memory = NotYetObservedMemoryPressure(),
            clock = clock,
            tracer = tracer,
            metrics = metrics,
            telemetry = record,
            reconnectPolicy =
                ReconnectPolicy(
                    ReconnectConfig(RECONNECT_BASE, RECONNECT_MAX, RECONNECT_JITTER),
                    Random.Default,
                ),
            scope = scope,
        )
    private val start = StartPipeline(pipeline, developmentModel::artifact, AndroidDeviceInfoProvider(context), record)
    private val stop = StopPipeline(pipeline)
    private val observe = ObservePipeline(pipeline, metrics)
    private val reportRendered = ReportFrameRendered(metrics)

    init {
        record(UsageTelemetry.AppSession)
        scope.launch(Dispatchers.IO) { developmentModel.install() }
    }

    fun liveViewModel(): LiveViewModel =
        LiveViewModel(
            observe = observe,
            start = start,
            stop = stop,
            reportRendered = reportRendered,
            display = pipeline.display,
            clock = clock,
            labelOf = developmentModel::labelOf,
            track = { trackUi(it) },
        )

    private companion object {
        val RECONNECT_BASE = 500.milliseconds
        val RECONNECT_MAX = 10.seconds
        const val RECONNECT_JITTER = 0.2
    }
}
