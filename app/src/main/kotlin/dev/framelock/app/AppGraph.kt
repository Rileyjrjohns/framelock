package dev.framelock.app

import android.content.Context
import android.util.Log
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.pipeline.PipelineUiState
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
import dev.framelock.live.system.AndroidMemoryPressureMonitor
import dev.framelock.live.system.AndroidTracer
import dev.framelock.live.system.PowerManagerThermalMonitor
import dev.framelock.live.system.SharedMemoryPressureMonitor
import dev.framelock.live.system.SharedThermalMonitor
import dev.framelock.live.ui.LiveViewModel
import dev.framelock.models.application.ActivateModel
import dev.framelock.models.application.ImportModel
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.application.RollbackModel
import dev.framelock.models.domain.ModelSource
import dev.framelock.models.domain.policy.ModelSwapPolicy
import dev.framelock.models.domain.policy.SwapDecision
import dev.framelock.models.store.FileAuditLog
import dev.framelock.models.store.FileModelRepository
import dev.framelock.models.ui.ModelRow
import dev.framelock.models.ui.ModelsViewModel
import dev.framelock.telemetry.application.TelemetryRecorder
import dev.framelock.telemetry.application.TrackScreen
import dev.framelock.telemetry.application.TrackUiInteraction
import dev.framelock.telemetry.room.LogcatTelemetrySink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
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

    private val metrics = MetricsAggregator(clock, record)

    // One sequence for every source: the pipeline refuses a session id that does not increase (ADR-016).
    private val sessionIds = SessionIdSequence()
    private val appContext = context.applicationContext
    private val labels =
        SourceLabels(
            auto = context.getString(R.string.source_auto),
            back = context.getString(R.string.source_back),
            front = context.getString(R.string.source_front),
            external = context.getString(R.string.source_external),
        )

    // Listed afresh each time the picker opens: devices come and go.
    private val sourceSelection =
        SourceSelection(labels) {
            buildSourceOptions(
                labels,
                hasBack = CameraXCameras.has(appContext, CameraFacing.BACK),
                hasFront = CameraXCameras.has(appContext, CameraFacing.FRONT),
                hasExternal = CameraXCameras.has(appContext, CameraFacing.EXTERNAL),
                uvcDevices = UvcDevices.list(appContext),
            )
        }
    private val uvcSource =
        UvcFrameSource(
            appContext,
            clock,
            tracer,
            sessionIds,
            selectedDeviceId = { (sourceSelection.choice as? SourceChoice.Uvc)?.deviceId },
        )
    private val source =
        SelectingFrameSource(
            external = CameraXFrameSource(appContext, clock, tracer, sessionIds, facing = CameraFacing.EXTERNAL),
            uvc = uvcSource,
            back = CameraXFrameSource(appContext, clock, tracer, sessionIds),
            front = CameraXFrameSource(appContext, clock, tracer, sessionIds, facing = CameraFacing.FRONT),
            choice = { sourceSelection.choice },
            hasExternalCamera = { CameraXCameras.has(appContext, CameraFacing.EXTERNAL) },
            hasUvcDevice = { UvcDevices.isAttached(appContext) },
            labels = labels,
            uvcDescription = uvcSource.description,
            scope = scope,
        )

    // The pipeline and the debug HUD share one subscription to each monitor (and so one headroom poller).
    private val thermal = SharedThermalMonitor(PowerManagerThermalMonitor(appContext), scope)
    private val memory = SharedMemoryPressureMonitor(AndroidMemoryPressureMonitor(appContext, clock), scope)
    private val pipeline =
        InferencePipeline(
            source = source,
            engine = LiteRtInferenceEngine(tracer),
            thermal = thermal,
            memory = memory,
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

    // Model store (ADR-003): verified imports, pointers on disk, audit trail. Keys come from assets/keys.
    private val modelRepository =
        FileModelRepository(
            appContext.filesDir,
            TrustedKeyAssets.load(appContext.assets) { name, error -> Log.w(TAG, "Trusted key $name ignored", error) },
        )
    private val auditLog = FileAuditLog(appContext.filesDir)
    private val importModel = ImportModel(pipeline, modelRepository, auditLog, record, clock)
    private val activateModel = ActivateModel(pipeline, modelRepository, auditLog, record, clock)
    private val rollbackModel = RollbackModel(pipeline, modelRepository, auditLog, record, clock)
    private val activeLabels =
        ActiveLabels(
            activeVersion = { modelRepository.active()?.manifest?.version },
            read = modelRepository::labels,
            onUnreadable = { Log.w(TAG, "Labels unreadable; the overlay shows class numbers", it) },
        )

    /** A model swap or import is allowed (`ModelSwapPolicy`): no session is connected. */
    private val canSwap: StateFlow<Boolean> =
        pipeline.state
            .map(::swapAllowed)
            .stateIn(scope, SharingStarted.Eagerly, swapAllowed(pipeline.state.value))
    private val start = StartPipeline(pipeline, modelRepository::active, AndroidDeviceInfoProvider(context), record)
    private val stop = StopPipeline(pipeline)
    private val observe = ObservePipeline(pipeline, metrics)
    private val reportRendered = ReportFrameRendered(metrics)

    init {
        record(UsageTelemetry.AppSession)
        scope.launch(Dispatchers.IO) { startUpModels() }
    }

    private fun swapAllowed(state: PipelineUiState): Boolean =
        ModelSwapPolicy.decide(state.state) == SwapDecision.Allowed

    /** Reads the labels of the model left active by the last run, then installs the bundled package if there is none. */
    private suspend fun startUpModels() {
        activeLabels.refresh()
        val bootstrap =
            ModelBootstrap(
                hasActiveModel = { modelRepository.active() != null },
                bundledPackage = ::bundledPackage,
                import = importModel::invoke,
                activate = activateModel::invoke,
            )
        val result = bootstrap.run()
        if (result is BootstrapResult.Refused) Log.w(TAG, "The bundled model was not installed: ${result.reason}")
        activeLabels.refresh()
    }

    /** The package of a debug build (`scripts/fetch-model.sh`); a release build has none. */
    private fun bundledPackage(): ModelSource? {
        val name =
            appContext.assets
                .list(BUNDLED_DIRECTORY)
                .orEmpty()
                .firstOrNull { it.endsWith(PACKAGE_SUFFIX) } ?: return null
        return object : ModelSource {
            override val displayName = name

            override fun open(): InputStream = appContext.assets.open("$BUNDLED_DIRECTORY/$name")
        }
    }

    private suspend fun listModelRows(): List<ModelRow> =
        withContext(Dispatchers.IO) {
            val active = modelRepository.active()?.manifest?.version
            val previous = modelRepository.previous()?.manifest?.version
            modelRepository.list().map { ModelRow(it.version, it.version == active, it.version == previous) }
        }

    /** The overlay's class names follow the model the swap just made active. */
    private suspend fun refreshLabelsAfter(result: ModelChangeResult): ModelChangeResult {
        if (result is ModelChangeResult.Changed) withContext(Dispatchers.IO) { activeLabels.refresh() }
        return result
    }

    fun liveViewModel(): LiveViewModel =
        LiveViewModel(
            observe = observe,
            start = start,
            stop = stop,
            reportRendered = reportRendered,
            display = pipeline.display,
            clock = clock,
            labelOf = activeLabels::labelOf,
            track = { trackUi(it) },
            listSources = sourceSelection::options,
            chooseSource = sourceSelection::select,
            selectedSource = sourceSelection.current,
            sourceDescription = source.activeDescription,
            thermal = thermal.state,
            memory = memory.state,
        )

    fun modelsViewModel(): ModelsViewModel =
        ModelsViewModel(
            listModels = ::listModelRows,
            importModel = importModel::invoke,
            activateModel = { version: ModelVersion -> refreshLabelsAfter(activateModel(version)) },
            rollbackModel = { refreshLabelsAfter(rollbackModel()) },
            canSwap = canSwap,
            track = { trackUi(it) },
        )

    private companion object {
        const val TAG = "AppGraph"
        const val BUNDLED_DIRECTORY = "models"
        const val PACKAGE_SUFFIX = ".flmodel"
        val RECONNECT_BASE = 500.milliseconds
        val RECONNECT_MAX = 10.seconds
        const val RECONNECT_JITTER = 0.2
    }
}
