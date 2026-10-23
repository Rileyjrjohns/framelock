package dev.framelock.live.application.usecase

import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.pipeline.PipelineHarness
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.telemetry.LiveTelemetry
import dev.framelock.testing.FakeDeviceInfoProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** StartPipeline, StopPipeline, ObservePipeline, ReportFrameRendered, TrackScreen, TrackUiInteraction. */
@OptIn(ExperimentalCoroutinesApi::class)
class PipelineUseCasesTest {
    private inner class UseCaseHarness(
        test: kotlinx.coroutines.test.TestScope,
    ) {
        val pipelineHarness = PipelineHarness(test)
        val pipeline get() = pipelineHarness.pipeline
        val engine get() = pipelineHarness.engine
        val model = pipelineHarness.model
        val deviceInfo = FakeDeviceInfoProvider()
        val recorded = mutableListOf<TelemetryEvent>()
        val record: (TelemetryEvent) -> Unit = { recorded += it }
        val start = StartPipeline(pipeline, activeModel = { model }, deviceInfo, record)
        val stop = StopPipeline(pipeline)
    }

    @Test
    fun `start opens the source and moves to Connecting`() =
        runTest {
            val h = UseCaseHarness(this)

            val result = h.start()
            runCurrent()

            assertEquals(StartResult.Started, result)
            assertEquals(PipelineState.Connecting, h.pipeline.state.value.state)
            assertEquals(1, h.pipelineHarness.source.openCalls)
        }

    @Test
    fun `start loads the active model before the first inference`() =
        runTest {
            val h = UseCaseHarness(this)

            h.start()

            assertEquals(listOf(h.model), h.engine.loadedModels)
        }

    @Test
    fun `start refuses without an active model`() =
        runTest {
            val h = UseCaseHarness(this)
            val empty = StartPipeline(h.pipeline, activeModel = { null }, h.deviceInfo, h.record)

            assertEquals(StartResult.NoActiveModel, empty())
            assertEquals(PipelineState.Idle, h.pipeline.state.value.state)
        }

    @Test
    fun `engine load failure at start gives a defined error and no crash`() =
        runTest {
            val h = UseCaseHarness(this)
            h.engine.failNextLoad()

            val result = h.start()

            assertTrue(result is StartResult.LoadFailed, "$result")
            assertEquals(PipelineState.Idle, h.pipeline.state.value.state)
        }

    @Test
    fun `second start while running is a no-op`() =
        runTest {
            val h = UseCaseHarness(this)
            h.start()
            runCurrent()

            val second = h.start()

            assertEquals(StartResult.AlreadyRunning, second)
            assertEquals(1, h.pipelineHarness.source.openCalls)
        }

    @Test
    fun `start records DeviceSnapshotRecorded once per start`() =
        runTest {
            val h = UseCaseHarness(this)

            h.start()
            h.start()

            assertEquals(
                1,
                h.recorded
                    .filterIsInstance<LiveTelemetry.DeviceSnapshotRecorded>()
                    .size,
            )
        }

    @Test
    fun `stop closes the source and returns to Idle`() =
        runTest {
            val h = UseCaseHarness(this)
            h.start()

            h.stop()

            assertEquals(1, h.pipelineHarness.source.closeCalls)
            assertEquals(PipelineState.Idle, h.pipeline.state.value.state)
        }

    @Test
    fun `stop releases every buffer`() =
        runTest {
            val h = UseCaseHarness(this)
            h.start()
            h.pipelineHarness.source.connect(SessionId(1))
            h.pipelineHarness.emit(h.pipelineHarness.frame(0))
            runCurrent()

            h.stop()

            h.pipelineHarness.pool.assertAllReleased()
        }

    @Test
    fun `stop when Idle is a no-op`() =
        runTest {
            val h = UseCaseHarness(this)

            h.stop()

            assertEquals(0, h.pipelineHarness.source.closeCalls)
        }

    @Test
    fun `observe exposes the pipeline state and a late subscriber receives the current state`() =
        runTest {
            val h = UseCaseHarness(this)
            val observe = ObservePipeline(h.pipeline, h.pipelineHarness.metrics)
            h.start()
            h.pipelineHarness.source.connect(SessionId(1))
            runCurrent()

            assertTrue(observe.state.value.state is PipelineState.Running)
        }

    @Test
    fun `report frame rendered forwards the render time to the aggregator`() =
        runTest {
            val h = UseCaseHarness(this)
            val report = ReportFrameRendered(h.pipelineHarness.metrics)
            h.start()
            h.pipelineHarness.source.connect(SessionId(1))
            val frame = h.pipelineHarness.frame(0)
            h.pipelineHarness.emit(frame)
            runCurrent()

            report(frame.id, frame.sessionId, frame.captureTimestampNanos + 25_000_000L)
            advanceTimeBy(10_000)
            runCurrent()
            h.pipelineHarness.metrics.tick()

            val window =
                h.pipelineHarness.telemetry
                    .filterIsInstance<LiveTelemetry.PipelineWindow>()
                    .single()
            assertEquals(25.0, window.e2eMsP50)
        }
}
