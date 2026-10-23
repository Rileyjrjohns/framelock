package dev.framelock.live.application.pipeline

import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.testing.FakeFrameSource
import dev.framelock.testing.FakeImageBufferPool
import dev.framelock.testing.FakeInferenceEngine
import dev.framelock.testing.FakeMemoryPressureMonitor
import dev.framelock.testing.FakeThermalMonitor
import dev.framelock.testing.ManualClock
import dev.framelock.testing.RecordingTracer
import dev.framelock.testing.TestFrames
import dev.framelock.testing.TestModels
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * start, stop and close on real threads (ADR-003): serialised, never lost, never interleaved, and a stop always
 * completes once begun.
 */
class InferencePipelineLifecycleTest {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "inference") }
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> uncaught += e })
    private val pool = FakeImageBufferPool(capacity = PipelineHarness.POOL_CAPACITY)
    private val source = FakeFrameSource()
    private val engine = FakeInferenceEngine(executor.asCoroutineDispatcher())
    private val model = TestModels.artifact("model-1")
    private val pipeline =
        InferencePipeline(
            source = source,
            engine = engine,
            thermal = FakeThermalMonitor(),
            memory = FakeMemoryPressureMonitor(),
            clock = ManualClock(),
            tracer = RecordingTracer(),
            metrics = MetricsAggregator(ManualClock(), {}),
            telemetry = {},
            reconnectPolicy = ReconnectPolicy(ReconnectConfig(10.milliseconds, 100.milliseconds, 0.0), Random(1)),
            scope = scope,
        )

    @AfterEach
    fun tearDown() {
        scope.cancel()
        executor.shutdownNow()
        assertTrue(uncaught.isEmpty(), "uncaught: $uncaught")
    }

    @Test
    fun `a stop whose caller is cancelled during a blocked inference still completes`() =
        runBlocking {
            startConnected()
            val latch = engine.blockNextInfer()
            source.emit(TestFrames.frame(pool, 0))
            awaitUntil { engine.inferredFrames.size == 1 }
            source.emit(TestFrames.frame(pool, 1))
            delay(50.milliseconds)

            val stopper = scope.launch { pipeline.stop() }
            delay(50.milliseconds)
            // The caller gives up (e.g. its scope is cleared) while the stop waits for the blocked inference.
            stopper.cancel()
            latch.countDown()
            awaitUntil { pipeline.state.value.state == PipelineState.Idle }

            assertEquals(1, source.closeCalls)
            pool.assertAllReleased()
        }

    @Test
    fun `a start issued during a stop waits for it and its session survives`() =
        runBlocking {
            startConnected()
            val latch = engine.blockNextInfer()
            source.emit(TestFrames.frame(pool, 0))
            awaitUntil { engine.inferredFrames.size == 1 }

            val stopper = scope.launch { pipeline.stop() }
            delay(50.milliseconds)
            val starter = scope.launch { pipeline.start(model) }
            delay(50.milliseconds)
            latch.countDown()
            stopper.join()
            starter.join()
            awaitUntil { source.isOpen }
            source.connect(SessionId(2))
            awaitUntil { pipeline.state.value.state is PipelineState.Running }

            assertEquals(1, source.closeCalls)
            assertTrue(source.isOpen)
        }

    @Test
    fun `a stop issued while start loads the model is honoured`() =
        runBlocking {
            val engineBusy = CountDownLatch(1)
            executor.execute { engineBusy.await() }

            val starter = scope.launch { pipeline.start(model) }
            delay(50.milliseconds)
            val stopper = scope.launch { pipeline.stop() }
            delay(50.milliseconds)
            engineBusy.countDown()
            starter.join()
            stopper.join()

            assertEquals(PipelineState.Idle, pipeline.state.value.state)
        }

    @Test
    fun `a close issued while start loads the model leaves the pipeline closed and idle`() =
        runBlocking {
            val engineBusy = CountDownLatch(1)
            executor.execute { engineBusy.await() }

            val starter = scope.launch { pipeline.start(model) }
            delay(50.milliseconds)
            val closer = scope.launch { pipeline.close() }
            delay(50.milliseconds)
            engineBusy.countDown()
            starter.join()
            closer.join()

            assertEquals(PipelineState.Idle, pipeline.state.value.state)
            assertTrue(engine.isClosed)
        }

    private suspend fun startConnected() {
        pipeline.start(model)
        source.connect(SessionId(1))
        awaitUntil { pipeline.state.value.state is PipelineState.Running }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) delay(5.milliseconds)
        }
    }
}
