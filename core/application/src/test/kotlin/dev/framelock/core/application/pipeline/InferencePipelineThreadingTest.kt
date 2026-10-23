package dev.framelock.core.application.pipeline

import dev.framelock.core.application.metrics.MetricsAggregator
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.policy.ReconnectConfig
import dev.framelock.core.domain.policy.ReconnectPolicy
import dev.framelock.core.testing.FakeFrameSource
import dev.framelock.core.testing.FakeImageBuffer
import dev.framelock.core.testing.FakeImageBufferPool
import dev.framelock.core.testing.FakeInferenceEngine
import dev.framelock.core.testing.FakeMemoryPressureMonitor
import dev.framelock.core.testing.FakeThermalMonitor
import dev.framelock.core.testing.ManualClock
import dev.framelock.core.testing.RecordingTracer
import dev.framelock.core.testing.TestFrames
import dev.framelock.core.testing.TestModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
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
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Rule 1 and ADR-002, on real threads: one inference at a time, on the engine's single thread. */
class InferencePipelineThreadingTest {
    private val inferenceThread: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, THREAD_NAME) }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pool = FakeImageBufferPool(capacity = PipelineHarness.POOL_CAPACITY)
    private val source = FakeFrameSource()
    private val engine = FakeInferenceEngine(inferenceThread)
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
        inferenceThread.close()
    }

    @Test
    fun `infer runs on the dedicated inference thread`() =
        runBlocking {
            startConnected()

            source.emit(TestFrames.frame(pool, 0))
            awaitUntil { engine.inferredFrames.size == 1 }

            assertEquals(setOf(THREAD_NAME), engine.inferThreads)
        }

    @Test
    fun `load and infer run on the same thread`() =
        runBlocking {
            startConnected()

            source.emit(TestFrames.frame(pool, 0))
            awaitUntil { engine.inferredFrames.size == 1 }

            assertEquals(engine.inferThreads, engine.loadThreads)
        }

    @Test
    fun `infer is never called concurrently`() =
        runBlocking {
            startConnected()
            val producers =
                List(4) { producer ->
                    // Children of the test: a failing producer fails the test.
                    launch(Dispatchers.Default) {
                        repeat(50) { i -> source.emit(TestFrames.frame(pool, (i * 4 + producer).toLong())) }
                    }
                }

            producers.forEach { it.join() }
            awaitUntil { pool.outstandingCount <= 1 }

            assertEquals(1, engine.maxConcurrentInfers)
        }

    @Test
    fun `the same thread serves load and infer across stop, start and model swaps`() =
        runBlocking {
            startConnected()
            source.emit(TestFrames.frame(pool, 0))
            awaitUntil { engine.inferredFrames.size == 1 }

            pipeline.stop()
            pipeline.loadModel(TestModels.artifact("model-2"))
            pipeline.start(TestModels.artifact("model-2"))
            // The source opens asynchronously; a closed source drops frames, as a real one would.
            awaitUntil { source.isOpen }
            source.connect(SessionId(2))
            source.emit(TestFrames.frame(pool, 0, session = 2))
            awaitUntil { engine.inferredFrames.size == 2 }

            assertEquals(setOf(THREAD_NAME), engine.inferThreads + engine.loadThreads)
        }

    @Test
    fun `engine is closed on the inference thread when the pipeline is closed`() =
        runBlocking {
            startConnected()

            pipeline.close()

            assertTrue(engine.isClosed)
            assertEquals(THREAD_NAME, engine.closeThread)
        }

    @Test
    fun `stop during a blocked inference discards the result and releases the buffer once infer returns`() =
        runBlocking {
            startConnected()
            val latch = engine.blockNextInfer()
            val frame = TestFrames.frame(pool, 0)
            source.emit(frame)
            awaitUntil { engine.inferredFrames.size == 1 }

            val stopping = scope.launch { pipeline.stop() }
            delay(50.milliseconds)
            val closedWhileBlocked = (frame.image as FakeImageBuffer).isClosed
            latch.countDown()
            stopping.join()

            assertEquals(false, closedWhileBlocked)
            assertTrue((frame.image as FakeImageBuffer).isClosed)
            assertEquals(null, pipeline.state.value.annotated)
            pool.assertAllReleased()
        }

    private suspend fun startConnected() {
        pipeline.start(TestModels.artifact("model-1"))
        source.connect(SessionId(1))
        awaitUntil { pipeline.state.value.state !is dev.framelock.core.domain.pipeline.PipelineState.Connecting }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) delay(5.milliseconds)
        }
    }

    private companion object {
        const val THREAD_NAME = "inference"
    }
}
