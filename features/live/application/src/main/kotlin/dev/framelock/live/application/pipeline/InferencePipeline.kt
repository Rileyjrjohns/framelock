package dev.framelock.live.application.pipeline

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.port.trace
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.metrics.DropReason
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.InferenceResult
import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.domain.pipeline.InferenceRate
import dev.framelock.live.domain.pipeline.PauseCause
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.policy.InferenceRatePolicy
import dev.framelock.live.domain.policy.RateDecision
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.live.domain.policy.ResultDisplayPolicy
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.InferenceEngine
import dev.framelock.live.domain.port.MemoryPressureMonitor
import dev.framelock.live.domain.port.SourceEvent
import dev.framelock.live.domain.port.ThermalMonitor
import dev.framelock.live.domain.telemetry.LiveTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Turns source events into annotated frames (docs/architecture.md, "Pipeline"):
 * - one inference at a time, on the engine's own thread (ADR-002);
 * - latest frame wins; late frames, frames from another session and frames received while paused are dropped and
 *   their buffers closed at once (ADR-010);
 * - a result is published only with the frame it was computed from, in the current session (ADR-001);
 * - the rate follows [InferenceRatePolicy] (ADR-013); disconnection gives `NoSignal` and reconnection follows
 *   [reconnectPolicy].
 *
 * Two coroutines share the session state: the event collector and the inference loop. They do so under [lock],
 * which is never held across a suspension, so that accepting a result, showing its frame and publishing the state
 * are atomic with respect to a disconnection.
 *
 * One cohesive state machine (splitting it would spread the locking), with every port injected explicitly.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class InferencePipeline(
    private val source: FrameSource,
    private val engine: InferenceEngine,
    private val thermal: ThermalMonitor,
    private val memory: MemoryPressureMonitor,
    private val clock: Clock,
    private val tracer: Tracer,
    private val metrics: MetricsAggregator,
    private val telemetry: (TelemetryEvent) -> Unit,
    private val reconnectPolicy: ReconnectPolicy,
    private val scope: CoroutineScope,
    private val metricsTickNanos: Long = METRICS_TICK_NANOS,
) {
    private val mutableState = MutableStateFlow(PipelineUiState.INITIAL)
    val state: StateFlow<PipelineUiState> = mutableState.asStateFlow()

    /** The frame on screen; the renderer reads its pixels here. */
    val display = DisplaySlot()

    /** Serialises session start and model loads, so a session never starts in the middle of a swap (ADR-003). */
    private val engineLock = Mutex()
    private var loadedModel: ModelVersion? = null

    private val lock = Any()
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var wakeUp = Channel<Unit>(Channel.CONFLATED)
    private var running = false
    private var connected = false
    private var session: SessionId? = null
    private var highestSession: SessionId? = null
    private var lastProcessed: FrameId? = null
    private var pending: Frame? = null
    private var lastInferenceStartNanos: Long? = null
    private var decision: RateDecision = RateDecision.Run(InferenceRate.UNLIMITED, cause = null)
    private var engineFailed = false
    private var reportedDegradation: DegradationCause? = null

    /** Loads [model] if needed, then opens the source. Does nothing if a session is already running. */
    suspend fun start(model: ModelArtifact) =
        engineLock.withLock {
            if (synchronized(lock) { running }) return@withLock
            loadLocked(model)
            val job = SupervisorJob(scope.coroutineContext[Job])
            val sessionScope = CoroutineScope(scope.coroutineContext + job)
            synchronized(lock) {
                running = true
                connected = false
                session = null
                lastProcessed = null
                lastInferenceStartNanos = null
                engineFailed = false
                reportedDegradation = null
                wakeUp = Channel(Channel.CONFLATED)
                sessionJob = job
                mutableState.update { PipelineUiState(PipelineState.Connecting, annotated = null, model = it.model) }
            }
            // Collect before opening: the source may emit as soon as it is open (ADR-010).
            sessionScope.launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect(::onEvent) }
            sessionScope.launch { observePressure() }
            sessionScope.launch { runInferenceLoop() }
            sessionScope.launch { tickMetrics() }
            sessionScope.launch { openFirst(sessionScope) }
        }

    /** Ends the session: no more inference, the source is closed, every buffer the pipeline holds is released. */
    suspend fun stop() {
        val job =
            synchronized(lock) {
                running = false
                connected = false
                sessionJob.also { sessionJob = null }
            } ?: return
        // Waits for an inference in progress: a blocking infer() cannot be interrupted.
        job.cancelAndJoin()
        synchronized(lock) { pending.also { pending = null } }?.let { drop(it, DropReason.STOPPED) }
        closeSourceQuietly()
        synchronized(lock) {
            display.clear()
            mutableState.update { PipelineUiState(PipelineState.Idle, annotated = null, model = it.model) }
        }
    }

    /** Loads [model] on the engine's thread. Only between sessions: the use cases check ModelSwapPolicy first. */
    suspend fun loadModel(model: ModelArtifact) =
        engineLock.withLock {
            check(!synchronized(lock) { running }) { "A model can only be loaded between sessions" }
            loadLocked(model)
        }

    /** Stops, then closes the engine on its own thread (ADR-002). */
    suspend fun close() {
        stop()
        engineLock.withLock { withContext(engine.dispatcher) { engine.close() } }
    }

    private suspend fun loadLocked(model: ModelArtifact) {
        val version = model.manifest.version
        if (loadedModel == version) return
        withContext(engine.dispatcher) { engine.load(model) }
        loadedModel = version
        mutableState.update { it.copy(model = version) }
    }

    private suspend fun openFirst(sessionScope: CoroutineScope) {
        if (attempt { source.open() } == null) onDisconnected(DisconnectReason.OPEN_FAILED, sessionScope)
    }

    private fun onEvent(event: SourceEvent) {
        when (event) {
            is SourceEvent.Connected -> onConnected(event.sessionId)
            is SourceEvent.FrameAvailable -> onFrame(event.frame)
            is SourceEvent.Disconnected -> onDisconnected(event.reason, sessionScope = null)
        }
    }

    private fun onConnected(sessionId: SessionId) =
        synchronized(lock) {
            val highest = highestSession
            if (!running || (highest != null && sessionId <= highest)) return@synchronized
            highestSession = sessionId
            session = sessionId
            connected = true
            lastProcessed = null
            reconnectJob?.cancel()
            reconnectJob = null
            publishLocked(annotated = null)
        }

    private fun onFrame(frame: Frame) {
        metrics.onFrameReceived()
        var reason: DropReason? = null
        var replaced: Frame? = null
        synchronized(lock) {
            val last = lastProcessed
            val waiting = pending
            reason =
                when {
                    !running -> DropReason.STOPPED
                    !connected || frame.sessionId != session -> DropReason.STALE_SESSION
                    last != null && !frame.id.isAfter(last) -> DropReason.LATE
                    waiting != null && !frame.id.isAfter(waiting.id) -> DropReason.LATE
                    decision is RateDecision.Pause -> DropReason.PAUSED
                    else -> null
                }
            if (reason == null) {
                replaced = waiting
                pending = frame
            }
        }
        reason?.let {
            drop(frame, it)
            return
        }
        replaced?.let { drop(it, DropReason.CONFLATED) }
        wakeUp.trySend(Unit)
    }

    private fun onDisconnected(
        reason: DisconnectReason,
        sessionScope: CoroutineScope?,
    ) {
        val released =
            synchronized(lock) {
                if (!running) return
                connected = false
                display.clear()
                mutableState.update {
                    PipelineUiState(
                        PipelineState.NoSignal(reason),
                        annotated = null,
                        model = it.model,
                    )
                }
                pending.also { pending = null }
            }
        released?.let { drop(it, DropReason.STALE_SESSION) }
        scheduleReconnect(sessionScope ?: CoroutineScope(scope.coroutineContext + (sessionJob ?: return)))
    }

    private fun scheduleReconnect(sessionScope: CoroutineScope) {
        val job =
            sessionScope.launch {
                var attempts = 0
                do {
                    delay(reconnectPolicy.delayFor(attempts++))
                } while (attempt { source.open() } == null)
            }
        synchronized(lock) {
            reconnectJob?.cancel()
            reconnectJob = job
        }
    }

    private suspend fun observePressure() {
        // A failing monitor completes its flow: combine keeps its last value, so the pipeline keeps going.
        combine(thermal.level.catch { }, memory.pressure.catch { }) { level, pressure ->
            InferenceRatePolicy.decide(level, pressure)
        }.distinctUntilChanged()
            .collect { applyDecision(it) }
    }

    private fun applyDecision(next: RateDecision) {
        val released =
            synchronized(lock) {
                decision = next
                if (connected) publishLocked(mutableState.value.annotated)
                if (next is RateDecision.Pause) pending.also { pending = null } else null
            }
        released?.let { drop(it, DropReason.PAUSED) }
        if (next !is RateDecision.Pause) wakeUp.trySend(Unit)
    }

    private suspend fun runInferenceLoop() {
        val signals = synchronized(lock) { wakeUp }
        while (true) {
            signals.receive()
            drainPending()
        }
    }

    /** Infers the pending frame until there is none, waiting between starts when the rate is capped. */
    private suspend fun drainPending() {
        while (true) {
            val waitNanos = synchronized(lock) { throttleWaitLocked() }
            if (waitNanos > 0) {
                delay(waitNanos.nanoseconds)
            } else {
                val frame = synchronized(lock) { pending.also { pending = null } } ?: return
                infer(frame)
            }
        }
    }

    private fun throttleWaitLocked(): Long {
        val run = decision as? RateDecision.Run
        val lastStart = lastInferenceStartNanos
        return if (run == null || lastStart == null) 0 else lastStart + run.rate.minIntervalNanos - clock.nowNanos()
    }

    private suspend fun infer(frame: Frame) {
        synchronized(lock) { lastInferenceStartNanos = clock.nowNanos() }
        var shown = false
        try {
            val result =
                attempt {
                    withContext(engine.dispatcher) { tracer.trace(TraceSection.INFERENCE) { engine.infer(frame) } }
                }
            if (result == null) onEngineFailure() else shown = show(frame, result)
        } finally {
            // Not shown: discarded, failed or cancelled. The pipeline still owns the buffer (ADR-010).
            if (!shown) frame.image.close()
        }
    }

    /** Publishes [result] with [frame] if both still belong to the current session; false if discarded. */
    private fun show(
        frame: Frame,
        result: InferenceResult,
    ): Boolean {
        synchronized(lock) {
            val current = running && connected && frame.sessionId == session
            if (!current || !ResultDisplayPolicy.isDisplayable(result, frame)) return false
            lastProcessed = frame.id
            engineFailed = false
            display.show(frame)
            publishLocked(
                AnnotatedFrame(
                    frame.id,
                    frame.sessionId,
                    frame.captureTimestampNanos,
                    frame.width,
                    frame.height,
                    result,
                ),
            )
        }
        metrics.onInferenceCompleted(frame.id, frame.sessionId, frame.captureTimestampNanos, result.inferenceNanos)
        return true
    }

    private fun onEngineFailure() =
        synchronized(lock) {
            engineFailed = true
            if (connected) publishLocked(mutableState.value.annotated)
        }

    /** Publishes the connected state for the current decision, and reports a new degradation once. */
    private fun publishLocked(annotated: AnnotatedFrame?) {
        val state = connectedStateLocked()
        mutableState.update { PipelineUiState(state, annotated, it.model) }
        val degradation =
            when (state) {
                is PipelineState.Degraded -> state.cause
                is PipelineState.Paused -> state.cause.asDegradation()
                else -> null
            }
        if (degradation != null &&
            degradation != reportedDegradation
        ) {
            telemetry(LiveTelemetry.PipelineDegraded(degradation))
        }
        reportedDegradation = degradation
    }

    private fun connectedStateLocked(): PipelineState =
        when (val current = decision) {
            is RateDecision.Pause -> {
                PipelineState.Paused(current.cause)
            }

            is RateDecision.Run -> {
                val cause = current.cause
                when {
                    engineFailed -> PipelineState.Degraded(DegradationCause.ENGINE_ERROR, current.rate)
                    cause != null -> PipelineState.Degraded(cause, current.rate)
                    else -> PipelineState.Running(current.rate)
                }
            }
        }

    private fun drop(
        frame: Frame,
        reason: DropReason,
    ) {
        frame.image.close()
        metrics.onFrameDropped(reason)
    }

    private suspend fun tickMetrics() {
        while (true) {
            delay(metricsTickNanos.nanoseconds)
            metrics.tick()
        }
    }

    private suspend fun closeSourceQuietly() {
        // Closing a source that already failed must not prevent the pipeline from stopping.
        attempt { source.close() }
    }

    private fun PauseCause.asDegradation(): DegradationCause =
        when (this) {
            PauseCause.THERMAL -> DegradationCause.THERMAL
            PauseCause.MEMORY -> DegradationCause.MEMORY
            PauseCause.THERMAL_AND_MEMORY -> DegradationCause.THERMAL_AND_MEMORY
        }

    private companion object {
        const val METRICS_TICK_NANOS = 1_000_000_000L
    }
}

/**
 * Runs a port call that may fail in any way; returns null if it threw. Cancellation is always rethrown. The pipeline
 * reacts to the failure itself (NoSignal, retry, degraded state: rule 5), so the exception is not needed.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private inline fun <T> attempt(block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
