package dev.framelock.live.application.pipeline

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
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
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.live.domain.telemetry.LiveTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.nanoseconds

/** Result of [InferencePipeline.start]. */
enum class SessionStart { STARTED, ALREADY_RUNNING, NO_MODEL }

/**
 * Turns source events into annotated frames (docs/architecture.md, "Pipeline"):
 * - one inference at a time, on the engine's own thread (ADR-002);
 * - latest frame wins; late frames, frames from another session and frames received while paused are dropped and
 *   their buffers closed at once (ADR-010);
 * - a result is published only with the frame it was computed from, in the current session (ADR-001);
 * - the rate follows [InferenceRatePolicy] (ADR-013); disconnection gives `NoSignal` and reconnection follows
 *   [reconnectPolicy];
 * - it degrades, it never crashes: a failing port or callback is contained (rule 5).
 *
 * Two locks, never held across a suspension together with another:
 * - [lifecycle] (a `Mutex`) serialises [start], [stop], [swapModel], [loadModel] and [close]: a session never starts
 *   in the middle of a model swap, and a stop is never lost or interleaved with a start (ADR-003);
 * - [lock] guards the session state shared by the event collector, the inference loop and the monitors. It is never
 *   held across a suspension, and no callback (telemetry, metrics publication) is called while holding it.
 *
 * One cohesive state machine (splitting it would spread the locking), with every port injected explicitly.
 */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
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
    private val monitorRetryNanos: Long = MONITOR_RETRY_NANOS,
) : ModelLoading {
    private val mutableState = MutableStateFlow(PipelineUiState.INITIAL)
    override val state: StateFlow<PipelineUiState> = mutableState.asStateFlow()

    /** The frame on screen; the renderer reads its pixels here (ADR-010). */
    val display = DisplaySlot()

    // Guarded by [lifecycle].
    private val lifecycle = Mutex()
    private var loadedManifest: ModelManifest? = null
    private var closed = false

    // Guarded by [lock].
    private val lock = Any()
    private var sessionJob: Job? = null
    private var sessionScope: CoroutineScope? = null
    private var reconnectJob: Job? = null
    private var disconnectedWhileReconnecting = false
    private var wakeUp = Channel<Unit>(Channel.CONFLATED)
    private var running = false
    private var connected = false
    private var session: SessionId? = null
    private var highestSession: SessionId? = null
    private var newestTaken: FrameId? = null
    private var pending: Frame? = null
    private var lastInferenceStartNanos: Long? = null
    private var thermalLevel = ThermalLevel.NONE
    private var memoryPressure = MemoryPressure.NORMAL
    private var decision = InferenceRatePolicy.decide(thermalLevel, memoryPressure)
    private var engineFailed = false
    private var reportedDegradation: DegradationCause? = null
    private var engineErrorReported = false
    private var reconnectAttempts = 0

    /** Starts a session on [model]; false if one is already running. Throws if the engine cannot load it. */
    suspend fun start(model: ModelArtifact): Boolean = start { model } == SessionStart.STARTED

    /**
     * Starts a session on the model [activeModel] returns, read under the lifecycle lock so that it cannot change
     * between the read and the load (ADR-003). Throws if the engine cannot load it; nothing starts then.
     */
    suspend fun start(activeModel: () -> ModelArtifact?): SessionStart =
        lifecycle.withLock {
            check(!closed) { "The pipeline is closed" }
            if (synchronized(lock) { running }) return@withLock SessionStart.ALREADY_RUNNING
            val model = activeModel() ?: return@withLock SessionStart.NO_MODEL
            loadLocked(model)
            openSession()
            SessionStart.STARTED
        }

    /** Ends the session: no more inference, the source is closed, every buffer the pipeline holds is released. */
    suspend fun stop() =
        lifecycle.withLock {
            // Once begun, a stop always completes: a cancelled caller must not leave a half-stopped pipeline.
            withContext(NonCancellable) { stopLocked() }
        }

    /** Stops, then closes the engine on its own thread (ADR-002). The pipeline cannot be started again. */
    suspend fun close() =
        lifecycle.withLock {
            withContext(NonCancellable) {
                stopLocked()
                if (!closed) {
                    closed = true
                    loadedManifest = null
                    attempt { withContext(engine.dispatcher) { engine.close() } }
                }
            }
        }

    /** Loads [model] between sessions (or without signal). Throws [SessionActiveException] or the engine's error. */
    suspend fun loadModel(model: ModelArtifact) =
        lifecycle.withLock {
            check(!closed) { "The pipeline is closed" }
            if (synchronized(lock) { running && connected }) throw SessionActiveException()
            loadLocked(model)
        }

    override suspend fun swapModel(
        target: ModelArtifact,
        restore: ModelArtifact?,
        commit: suspend () -> Result<Unit>,
    ): SwapOutcome =
        lifecycle.withLock {
            check(!closed) { "The pipeline is closed" }
            if (synchronized(lock) { running && connected }) return@withLock SwapOutcome.SessionActive
            val loadFailure = failureOf { loadLocked(target) }
            if (loadFailure != null) return@withLock SwapOutcome.LoadFailed(loadFailure)
            withContext(NonCancellable) {
                val commitFailure = failureOf { commit().getOrThrow() }
                if (commitFailure == null) {
                    SwapOutcome.Swapped
                } else {
                    val restored = restore != null && failureOf { loadLocked(restore) } == null
                    SwapOutcome.CommitFailed(commitFailure, restored)
                }
            }
        }

    private suspend fun loadLocked(model: ModelArtifact) {
        // Compared on the whole manifest: a same-version artifact with other content is loaded again.
        if (loadedManifest == model.manifest) return
        withContext(engine.dispatcher) { engine.load(model) }
        loadedManifest = model.manifest
        mutableState.update { it.copy(model = model.manifest.version) }
    }

    private fun openSession() {
        val job = SupervisorJob(scope.coroutineContext[Job])
        val newScope = CoroutineScope(scope.coroutineContext + job)
        synchronized(lock) {
            running = true
            connected = false
            session = null
            newestTaken = null
            lastInferenceStartNanos = null
            thermalLevel = ThermalLevel.NONE
            memoryPressure = MemoryPressure.NORMAL
            decision = InferenceRatePolicy.decide(thermalLevel, memoryPressure)
            engineFailed = false
            reportedDegradation = null
            engineErrorReported = false
            reconnectAttempts = 0
            wakeUp = Channel(Channel.CONFLATED)
            sessionJob = job
            sessionScope = newScope
            mutableState.update { PipelineUiState(PipelineState.Connecting, annotated = null, model = it.model) }
        }
        // Collect before opening: the source may emit as soon as it is open (ADR-010).
        newScope.launch(start = CoroutineStart.UNDISPATCHED) { collectEvents() }
        newScope.launch { follow(thermal.level) { onThermal(it) } }
        newScope.launch { follow(memory.pressure) { onMemory(it) } }
        newScope.launch { runInferenceLoop() }
        newScope.launch { tickMetrics() }
        newScope.launch { if (attempt { source.open() } == null) onDisconnected(DisconnectReason.OPEN_FAILED) }
    }

    private suspend fun stopLocked() {
        val job =
            synchronized(lock) {
                running = false
                connected = false
                sessionScope = null
                reconnectJob = null
                sessionJob.also { sessionJob = null }
            } ?: return
        // Waits for an inference in progress: a blocking infer() cannot be interrupted.
        job.cancelAndJoin()
        synchronized(lock) { pending.also { pending = null } }?.let { drop(it, DropReason.STOPPED) }
        attempt { source.close() }
        guard { metrics.endSession() }
        synchronized(lock) {
            display.clear()
            mutableState.update { PipelineUiState(PipelineState.Idle, annotated = null, model = it.model) }
        }
    }

    /** A failing event flow is a lost stream: NoSignal, then the flow is collected again after a pause. */
    private suspend fun collectEvents() {
        while (true) {
            val completed = attempt { source.events.collect { onEvent(it) } }
            if (completed != null) return
            onDisconnected(DisconnectReason.STREAM_ERROR)
            delay(reconnectPolicy.delayFor(0))
        }
    }

    private fun onEvent(event: SourceEvent) {
        when (event) {
            is SourceEvent.Connected -> onConnected(event.sessionId)
            is SourceEvent.FrameAvailable -> onFrame(event.frame)
            is SourceEvent.Disconnected -> onDisconnected(event.reason)
        }
    }

    private fun onConnected(sessionId: SessionId) {
        var released: Frame? = null
        var event: TelemetryEvent? = null
        synchronized(lock) {
            val highest = highestSession
            if (!running || (highest != null && sessionId <= highest)) return
            highestSession = sessionId
            session = sessionId
            connected = true
            newestTaken = null
            engineErrorReported = false
            // A new session replaces the previous one even without a Disconnected in between.
            released = pending.also { pending = null }
            display.clear()
            event = publishLocked(annotated = null)
        }
        released?.let { drop(it, DropReason.STALE_SESSION) }
        report(event)
    }

    private fun onFrame(frame: Frame) {
        // The frame is ours from here on: no callback may prevent it from being closed or kept.
        guard { metrics.onFrameReceived() }
        var reason: DropReason? = null
        var replaced: Frame? = null
        synchronized(lock) {
            val waiting = pending
            val newest = listOfNotNull(newestTaken, waiting?.id).maxOrNull()
            reason =
                when {
                    !running -> DropReason.STOPPED
                    !connected || frame.sessionId != session -> DropReason.STALE_SESSION
                    newest != null && !frame.id.isAfter(newest) -> DropReason.LATE
                    decision is RateDecision.Pause -> DropReason.PAUSED
                    else -> null
                }
            if (reason == null) {
                replaced = waiting
                pending = frame
                // Frames flow again: the stream works, so the reconnection backoff starts over (ADR-013, rule 4).
                reconnectAttempts = 0
            }
        }
        reason?.let {
            drop(frame, it)
            return
        }
        replaced?.let { drop(it, DropReason.CONFLATED) }
        wakeUp.trySend(Unit)
    }

    private fun onDisconnected(reason: DisconnectReason) {
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
        // A refused permission needs the user: retrying would only prompt again.
        if (reason != DisconnectReason.PERMISSION_DENIED) scheduleReconnect()
    }

    /** One reconnection loop at a time; its backoff keeps growing until frames flow again. */
    private fun scheduleReconnect() =
        synchronized(lock) {
            if (reconnectJob?.isActive == true) {
                // Raised while the loop is opening: the loop must not take its open() for a success.
                disconnectedWhileReconnecting = true
                return@synchronized
            }
            val target = sessionScope ?: return@synchronized
            reconnectJob = target.launch { reconnect() }
        }

    private suspend fun reconnect() {
        while (true) {
            val attemptNumber = synchronized(lock) { reconnectAttempts++ }
            delay(reconnectPolicy.delayFor(attemptNumber))
            // Reconnected meanwhile (the source recovered by itself): nothing to do. An open() in progress is never
            // cancelled by a Connected event.
            val stop =
                synchronized(lock) {
                    disconnectedWhileReconnecting = false
                    (connected || !running).also { if (it) leaveReconnect() }
                }
            if (stop) return
            val opened = attempt { source.open() } != null
            if (opened && synchronized(lock) { !disconnectedWhileReconnecting && leaveReconnect() }) return
        }
    }

    /**
     * Called under [lock] when the loop decides to end: it unregisters itself at once, so a disconnection raised
     * before the coroutine completes starts a new loop instead of being taken for one raised during this loop's open.
     */
    private fun leaveReconnect(): Boolean {
        reconnectJob = null
        return true
    }

    /** Follows a monitor; if it fails, the last known level stays in force and the monitor is subscribed again. */
    private suspend fun <T> follow(
        flow: Flow<T>,
        onValue: (T) -> Unit,
    ) {
        while (true) {
            attempt { flow.collect { onValue(it) } }
            delay(monitorRetryNanos.nanoseconds)
        }
    }

    private fun onThermal(level: ThermalLevel) = applyDecision { thermalLevel = level }

    private fun onMemory(pressure: MemoryPressure) = applyDecision { memoryPressure = pressure }

    private inline fun applyDecision(update: () -> Unit) {
        var released: Frame? = null
        var event: TelemetryEvent? = null
        val next =
            synchronized(lock) {
                update()
                val next = InferenceRatePolicy.decide(thermalLevel, memoryPressure)
                if (next == decision) return
                decision = next
                if (connected) event = publishLocked(mutableState.value.annotated)
                if (next is RateDecision.Pause) released = pending.also { pending = null }
                next
            }
        released?.let { drop(it, DropReason.PAUSED) }
        report(event)
        // Also wakes a throttle wait, so a relaxed rate takes effect at once.
        if (next !is RateDecision.Pause) wakeUp.trySend(Unit)
    }

    private suspend fun runInferenceLoop() {
        val signals = synchronized(lock) { wakeUp }
        while (true) {
            signals.receive()
            drainPending(signals)
        }
    }

    /** Infers the pending frame until there is none, waiting between starts when the rate is capped. */
    private suspend fun drainPending(signals: Channel<Unit>) {
        while (true) {
            val waitNanos = synchronized(lock) { throttleWaitLocked() }
            if (waitNanos > 0) {
                // Woken early by a new frame or a new decision: the wait is computed again.
                withTimeoutOrNull(waitNanos.nanoseconds) { signals.receive() }
            } else {
                val frame =
                    synchronized(lock) {
                        pending?.also {
                            pending = null
                            newestTaken = it.id
                        }
                    } ?: return
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
        var reason = DropReason.STOPPED
        try {
            val result =
                attempt {
                    withContext(engine.dispatcher) { tracer.trace(TraceSection.INFERENCE) { engine.infer(frame) } }
                }
            if (result == null) {
                reason = DropReason.ENGINE_ERROR
                onEngineFailure()
            } else {
                // Recorded before the state is published, so a renderer reacting at once finds the capture time.
                guard {
                    metrics.onInferenceCompleted(
                        frame.id,
                        frame.sessionId,
                        frame.captureTimestampNanos,
                        result.inferenceNanos,
                    )
                }
                reason = DropReason.STALE_SESSION
                shown = show(frame, result)
            }
        } finally {
            // Not shown: failed, discarded or cancelled. The pipeline still owns the buffer (ADR-010).
            if (!shown) {
                frame.image.close()
                // A discarded result was analysed: it counts as completed, not dropped (ADR-013).
                if (reason != DropReason.STALE_SESSION) guard { metrics.onFrameDropped(reason) }
            }
        }
    }

    /** Publishes [result] with [frame] if both still belong to the current session; false if discarded. */
    private fun show(
        frame: Frame,
        result: InferenceResult,
    ): Boolean {
        val event: TelemetryEvent?
        synchronized(lock) {
            val current = running && connected && frame.sessionId == session
            if (!current || !ResultDisplayPolicy.isDisplayable(result, frame)) return false
            engineFailed = false
            display.show(frame)
            event =
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
        report(event)
        return true
    }

    private fun onEngineFailure() {
        var event: TelemetryEvent? = null
        synchronized(lock) {
            engineFailed = true
            if (connected) event = publishLocked(mutableState.value.annotated)
        }
        report(event)
    }

    /**
     * Publishes the connected state for the current decision. Returns the telemetry event to report, outside the
     * lock: a new degradation once per transition, and an engine error at most once per session (ADR-004).
     */
    private fun publishLocked(annotated: AnnotatedFrame?): TelemetryEvent? {
        val state = connectedStateLocked()
        mutableState.update { PipelineUiState(state, annotated, it.model) }
        val degradation =
            when (state) {
                is PipelineState.Degraded -> state.cause
                is PipelineState.Paused -> state.cause.asDegradation()
                else -> null
            }
        val isNew = degradation != null && degradation != reportedDegradation
        val alreadyReported = degradation == DegradationCause.ENGINE_ERROR && engineErrorReported
        reportedDegradation = degradation
        if (!isNew || alreadyReported) return null
        if (degradation == DegradationCause.ENGINE_ERROR) engineErrorReported = true
        return LiveTelemetry.PipelineDegraded(checkNotNull(degradation))
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
        guard { metrics.onFrameDropped(reason) }
    }

    private fun report(event: TelemetryEvent?) {
        if (event != null) guard { telemetry(event) }
    }

    private suspend fun tickMetrics() {
        while (true) {
            delay(metricsTickNanos.nanoseconds)
            guard { metrics.tick() }
        }
    }

    private fun PauseCause.asDegradation(): DegradationCause =
        when (this) {
            PauseCause.THERMAL -> DegradationCause.THERMAL
            PauseCause.MEMORY -> DegradationCause.MEMORY
            PauseCause.THERMAL_AND_MEMORY -> DegradationCause.THERMAL_AND_MEMORY
        }

    private companion object {
        const val METRICS_TICK_NANOS = 1_000_000_000L
        const val MONITOR_RETRY_NANOS = 1_000_000_000L
    }
}

/**
 * Runs a port call that may fail in any way; returns null if it threw. Cancellation is always rethrown. The pipeline
 * reacts to the failure itself (NoSignal, retry, degraded state: rule 5), so the exception is not needed.
 * [LinkageError] is included: a native runtime that fails to link must degrade the pipeline, not crash the app.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal inline fun <T> attempt(block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }

/** Like [attempt], but returns the failure, or null on success. */
@Suppress("TooGenericExceptionCaught")
internal inline fun failureOf(block: () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    } catch (e: LinkageError) {
        e
    }

/** A callback (metrics, telemetry) that must never break the pipeline. */
internal inline fun guard(block: () -> Unit) {
    attempt(block)
}
