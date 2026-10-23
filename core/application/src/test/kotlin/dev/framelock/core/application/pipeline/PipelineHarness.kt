package dev.framelock.core.application.pipeline

import dev.framelock.core.application.metrics.MetricsAggregator
import dev.framelock.core.domain.frame.Frame
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.model.ModelArtifact
import dev.framelock.core.domain.policy.ReconnectConfig
import dev.framelock.core.domain.policy.ReconnectPolicy
import dev.framelock.core.domain.telemetry.TelemetryEvent
import dev.framelock.core.testing.FakeClock
import dev.framelock.core.testing.FakeFrameSource
import dev.framelock.core.testing.FakeImageBufferPool
import dev.framelock.core.testing.FakeInferenceEngine
import dev.framelock.core.testing.FakeMemoryPressureMonitor
import dev.framelock.core.testing.FakeThermalMonitor
import dev.framelock.core.testing.RecordingTracer
import dev.framelock.core.testing.TestFrames
import dev.framelock.core.testing.TestModels
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import java.util.Collections
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A pipeline wired to fakes, on the test's virtual time. The engine runs on a test dispatcher sharing the
 * scheduler unless a real one is given; every state emission is recorded in [states].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PipelineHarness(
    private val test: TestScope,
    engineDispatcher: CoroutineDispatcher = StandardTestDispatcher(test.testScheduler),
) {
    /** Sized for bursts that allocate many frames before the pipeline can release any. */
    val pool = FakeImageBufferPool(capacity = POOL_CAPACITY)
    val source = FakeFrameSource()
    val engine = FakeInferenceEngine(engineDispatcher)
    val thermal = FakeThermalMonitor()
    val memory = FakeMemoryPressureMonitor()
    val clock = FakeClock { test.testScheduler.currentTime * NANOS_PER_MILLI }
    val tracer = RecordingTracer()
    val telemetry: MutableList<TelemetryEvent> = Collections.synchronizedList(mutableListOf())
    val metrics = MetricsAggregator(clock, { telemetry += it })
    val reconnectConfig = ReconnectConfig(baseDelay = 100.milliseconds, maxDelay = 2.seconds, jitter = 0.0)
    val model: ModelArtifact = TestModels.artifact("model-1")

    val pipeline =
        InferencePipeline(
            source = source,
            engine = engine,
            thermal = thermal,
            memory = memory,
            clock = clock,
            tracer = tracer,
            metrics = metrics,
            telemetry = { telemetry += it },
            reconnectPolicy = ReconnectPolicy(reconnectConfig, Random(SEED)),
            scope = test.backgroundScope,
        )

    val states: MutableList<PipelineUiState> = Collections.synchronizedList(mutableListOf())

    init {
        test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { pipeline.state.toList(states) }
    }

    fun frame(
        id: Long,
        session: Long = 1,
    ): Frame = TestFrames.frame(pool, id, session)

    /** Starts the pipeline and connects session [session]. */
    suspend fun startConnected(session: Long = 1) {
        pipeline.start(model)
        source.connect(SessionId(session))
        test.runCurrent()
    }

    fun emit(vararg frames: Frame) = frames.forEach { source.emit(it) }

    val current: PipelineUiState get() = pipeline.state.value

    companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val SEED = 1
        const val POOL_CAPACITY = 1024
    }
}
