package dev.framelock.live.application.pipeline

import dev.framelock.live.application.metrics.DropReason
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.usecase.ReportFrameRendered
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.InferenceResult
import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.domain.pipeline.PauseCause
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import dev.framelock.live.domain.port.ThermalMonitor
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.live.domain.telemetry.LiveTelemetry
import dev.framelock.testing.FakeClock
import dev.framelock.testing.FakeFrameSource
import dev.framelock.testing.FakeImageBuffer
import dev.framelock.testing.FakeImageBufferPool
import dev.framelock.testing.FakeInferenceEngine
import dev.framelock.testing.FakeMemoryPressureMonitor
import dev.framelock.testing.FakeThermalMonitor
import dev.framelock.testing.RecordingTracer
import dev.framelock.testing.TestFrames
import dev.framelock.testing.TestModels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Failure paths and edge cases (rule 5: degrade, never crash), on virtual time. Uncaught exceptions fail `runTest`,
 * so every test here also checks that nothing escapes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineRobustnessTest {
    @Test
    fun `a result computed for another frame is never published (ADR-001)`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.resultFor = { f ->
                InferenceResult(FrameId(f.id.value + 1), f.sessionId, h.model.manifest.version, emptyList(), 1)
            }

            h.emit(h.frame(0))
            runCurrent()

            assertNull(h.current.annotated)
            h.pool.assertAllReleased()
        }

    @Test
    fun `a failed first open gives NoSignal(OPEN_FAILED) and is retried after the backoff`() =
        runTest {
            val h = PipelineHarness(this)
            h.source.failNextOpens(1)

            h.pipeline.start(h.model)
            runCurrent()
            val afterFailure = h.current.state
            advanceTimeBy(100)
            runCurrent()

            assertEquals(PipelineState.NoSignal(DisconnectReason.OPEN_FAILED), afterFailure)
            assertEquals(2, h.source.openCalls)
        }

    @Test
    fun `a refused permission is not retried`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.source.disconnect(DisconnectReason.PERMISSION_DENIED)
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(PipelineState.NoSignal(DisconnectReason.PERMISSION_DENIED), h.current.state)
            assertEquals(1, h.source.openCalls)
        }

    @Test
    fun `Connected during the backoff stops the reconnection attempts`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()
            advanceTimeBy(50)

            h.source.connect(SessionId(2))
            runCurrent()
            val opens = h.source.openCalls
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(opens, h.source.openCalls)
        }

    @Test
    fun `backoff keeps growing when the source opens but the stream dies at once`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.onOpen = { n ->
                h.source.connect(SessionId(n.toLong() + 1))
                h.source.disconnect(DisconnectReason.STREAM_ERROR)
            }

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()

            // 100, 200, 400, 800, 1600, 2000, 2000, 2000 ms: about 8 attempts in 10 s, not one every 100 ms.
            assertTrue(h.source.openCalls < 12, "opens=${h.source.openCalls}")
        }

    @Test
    fun `a duplicate Disconnected does not restart the backoff`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.failNextOpens(1_000)

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()
            repeat(100) {
                advanceTimeBy(100)
                h.source.disconnect(DisconnectReason.OPEN_FAILED)
                runCurrent()
            }

            assertTrue(h.source.openCalls < 12, "opens=${h.source.openCalls}")
        }

    @Test
    fun `a throwing event flow gives NoSignal(STREAM_ERROR) and is collected again`() =
        runTest {
            val events = MutableSharedFlow<SourceEvent>()
            var subscriptions = 0
            val source =
                object : FrameSource {
                    override val events: Flow<SourceEvent> =
                        flow {
                            subscriptions++
                            check(subscriptions != 1) { "usb stack failure" }
                            events.collect { emit(it) }
                        }

                    override suspend fun open() = Unit

                    override suspend fun close() = Unit
                }
            val pipeline = pipeline(this, source = source)

            pipeline.start(TestModels.artifact("m"))
            runCurrent()
            val afterFailure = pipeline.state.value.state
            advanceTimeBy(1_000)
            runCurrent()
            events.emit(SourceEvent.Connected(TestFrames.SPEC, SessionId(1)))
            runCurrent()

            assertEquals(PipelineState.NoSignal(DisconnectReason.STREAM_ERROR), afterFailure)
            assertTrue(pipeline.state.value.state is PipelineState.Running, "${pipeline.state.value.state}")
        }

    @Test
    fun `a pause arriving while a frame waits for the throttle drops that frame`() =
        runTest {
            val h = PipelineHarness(this)
            h.thermal.set(ThermalLevel.SEVERE)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()
            h.emit(h.frame(1))
            advanceTimeBy(10)
            runCurrent()

            h.thermal.set(ThermalLevel.EMERGENCY)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            h.metrics.tick()

            assertEquals(listOf(1L to 0L), h.engine.inferredFrames)
            assertEquals(1L, h.metrics.live.value.dropsByReason[DropReason.PAUSED])
        }

    @Test
    fun `relaxing the rate during a throttle wait starts the pending inference at once`() =
        runTest {
            val h = PipelineHarness(this)
            h.thermal.set(ThermalLevel.CRITICAL)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()
            h.emit(h.frame(1))
            advanceTimeBy(10)
            runCurrent()

            h.thermal.set(ThermalLevel.NONE)
            runCurrent()

            assertEquals(listOf(1L to 0L, 1L to 1L), h.engine.inferredFrames)
        }

    @Test
    fun `memory pressure applies although the thermal monitor has not emitted yet`() =
        runTest {
            val silentThermal =
                object : ThermalMonitor {
                    override val level: Flow<ThermalLevel> = MutableSharedFlow()
                }
            val memory = FakeMemoryPressureMonitor()
            val source = FakeFrameSource()
            val pipeline = pipeline(this, source = source, thermal = silentThermal, memory = memory)
            pipeline.start(TestModels.artifact("m"))
            source.connect(SessionId(1))
            runCurrent()

            memory.set(MemoryPressure.CRITICAL)
            runCurrent()

            assertEquals(PipelineState.Paused(PauseCause.MEMORY), pipeline.state.value.state)
        }

    @Test
    fun `a monitor that fails while paused is followed again and the pipeline resumes when the device cools`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.thermal.set(ThermalLevel.EMERGENCY)
            runCurrent()
            h.thermal.fail(IllegalStateException("thermal service died"))
            runCurrent()

            h.thermal.set(ThermalLevel.NONE)
            advanceTimeBy(1_100)
            runCurrent()

            assertTrue(h.current.state is PipelineState.Running, "${h.current.state}")
        }

    @Test
    fun `a frame older than the one being inferred is dropped as late`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 5L) h.emit(h.frame(4)) }

            h.emit(h.frame(5))
            runCurrent()

            assertEquals(listOf(1L to 5L), h.engine.inferredFrames)
            assertEquals(
                5L,
                h.current.annotated
                    ?.frameId
                    ?.value,
            )
        }

    @Test
    fun `Connected without a Disconnected clears the screen and the pending frame`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected(session = 1)
            h.emit(h.frame(0))
            runCurrent()

            h.source.connect(SessionId(2))
            runCurrent()

            h.pipeline.display.read { assertNull(it) }
            assertNull(h.current.annotated)
            h.pool.assertAllReleased()
        }

    @Test
    fun `an engine failure counts as a dropped frame and every frame is accounted for`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.failInferWhen = { it.id.value == 0L }

            h.emit(h.frame(0))
            runCurrent()
            h.emit(h.frame(1))
            runCurrent()
            h.metrics.tick()

            assertEquals(1L, h.metrics.live.value.dropsByReason[DropReason.ENGINE_ERROR])
            assertEquals(1L, h.metrics.live.value.droppedFrames)
        }

    @Test
    fun `an intermittent engine error is reported once per session, not per failure`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.failInferWhen = { it.id.value % 2 == 0L }

            repeat(200) { id ->
                h.emit(h.frame(id.toLong()))
                advanceTimeBy(33)
                runCurrent()
            }

            val engineErrors =
                h.telemetry.filterIsInstance<LiveTelemetry.PipelineDegraded>().filter {
                    it.cause == DegradationCause.ENGINE_ERROR
                }
            assertEquals(1, engineErrors.size)
        }

    @Test
    fun `a telemetry sink that throws never closes the frame on screen`() =
        runTest {
            val h = PipelineHarness(this, onTelemetry = { error("sink full") })
            h.startConnected()
            h.thermal.set(ThermalLevel.SEVERE)
            runCurrent()

            h.emit(h.frame(0))
            runCurrent()

            val shownAndClosed = h.pipeline.display.read { f -> f != null && (f.image as FakeImageBuffer).isClosed }
            assertFalse(shownAndClosed)
            assertEquals(
                0L,
                h.current.annotated
                    ?.frameId
                    ?.value,
            )
        }

    @Test
    fun `a metrics sink that throws does not leak frames nor stop the pipeline`() =
        runTest {
            val pool = FakeImageBufferPool()
            val source = FakeFrameSource()
            var failing = false
            val clock = FakeClock { testScheduler.currentTime * 1_000_000L }
            val metrics = MetricsAggregator(clock, { check(!failing) { "sink" } })
            val pipeline = pipeline(this, source = source, metrics = metrics)
            pipeline.start(TestModels.artifact("m"))
            source.connect(SessionId(1))
            source.emit(TestFrames.frame(pool, 0))
            runCurrent()

            failing = true
            advanceTimeBy(10_000)
            runCurrent()
            source.emit(TestFrames.frame(pool, 1))
            runCurrent()
            pipeline.stop()

            pool.assertAllReleased()
        }

    @Test
    fun `the pipeline ticks the aggregator by itself`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.emit(h.frame(0))
            runCurrent()
            advanceTimeBy(1_100)
            runCurrent()

            assertEquals(5.0, h.metrics.live.value.inferenceMs)
        }

    @Test
    fun `the e2e sample is kept when the renderer reports from an inline state collector`() =
        runTest {
            val h = PipelineHarness(this)
            val report = ReportFrameRendered(h.metrics)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                h.pipeline.state.collect { s ->
                    s.annotated?.let { report(it.frameId, it.sessionId, it.captureTimestampNanos + 20_000_000L) }
                }
            }
            h.startConnected()

            h.emit(h.frame(0))
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            h.metrics.tick()

            val window = h.telemetry.filterIsInstance<LiveTelemetry.PipelineWindow>().single()
            assertNotNull(window.e2eMsP50)
        }

    @Test
    fun `a window cut by stop is published at stop with its own rate`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            repeat(30) { id ->
                h.emit(h.frame(id.toLong()))
                advanceTimeBy(100)
                runCurrent()
            }

            h.pipeline.stop()
            advanceTimeBy(300_000)
            runCurrent()
            h.startConnected(session = 2)
            advanceTimeBy(1_100)
            runCurrent()

            val window = h.telemetry.filterIsInstance<LiveTelemetry.PipelineWindow>().single()
            assertEquals(10.0, window.inputFps, 0.5)
        }

    @Test
    fun `HUD rates after a restart do not span the idle period`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.pipeline.stop()
            advanceTimeBy(300_000)
            runCurrent()
            h.startConnected(session = 2)

            repeat(10) {
                h.emit(h.frame(it.toLong(), session = 2))
                advanceTimeBy(100)
                runCurrent()
            }
            advanceTimeBy(1)
            runCurrent()

            assertTrue(h.metrics.live.value.inputFps > 5.0, "inputFps=${h.metrics.live.value.inputFps}")
        }

    @Test
    fun `a stop requested while the model is loading takes effect after the start`() =
        runTest {
            val h = PipelineHarness(this)
            val starting = launch { h.pipeline.start(h.model) }
            val stopping = launch { h.pipeline.stop() }

            starting.join()
            stopping.join()

            assertEquals(PipelineState.Idle, h.current.state)
            assertEquals(1, h.source.closeCalls)
        }

    @Test
    fun `a closed pipeline refuses to start`() =
        runTest {
            val h = PipelineHarness(this)
            h.pipeline.close()

            val failure = runCatching { h.pipeline.start(h.model) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException, "$failure")
            assertTrue(h.engine.isClosed)
        }

    private fun pipeline(
        test: TestScope,
        source: FrameSource,
        thermal: ThermalMonitor = FakeThermalMonitor(),
        memory: FakeMemoryPressureMonitor = FakeMemoryPressureMonitor(),
        metrics: MetricsAggregator? = null,
    ): InferencePipeline {
        val clock = FakeClock { test.testScheduler.currentTime * 1_000_000L }
        return InferencePipeline(
            source,
            FakeInferenceEngine(StandardTestDispatcher(test.testScheduler)),
            thermal,
            memory,
            clock,
            RecordingTracer(),
            metrics ?: MetricsAggregator(clock, {}),
            {},
            ReconnectPolicy(ReconnectConfig(100.milliseconds, 2.seconds, 0.0), Random(1)),
            test.backgroundScope,
        )
    }
}
