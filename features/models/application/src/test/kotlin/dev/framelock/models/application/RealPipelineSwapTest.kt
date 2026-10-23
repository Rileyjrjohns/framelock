package dev.framelock.models.application

import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.usecase.StartPipeline
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.testing.FakeAuditLog
import dev.framelock.testing.FakeClock
import dev.framelock.testing.FakeDeviceInfoProvider
import dev.framelock.testing.FakeFrameSource
import dev.framelock.testing.FakeInferenceEngine
import dev.framelock.testing.FakeMemoryPressureMonitor
import dev.framelock.testing.FakeModelRepository
import dev.framelock.testing.FakeModelRepository.Operation
import dev.framelock.testing.FakeThermalMonitor
import dev.framelock.testing.RecordingTracer
import dev.framelock.testing.TestModels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The model use cases on the real [InferencePipeline] (ADR-003): what the fake of the other tests stands for. */
@OptIn(ExperimentalCoroutinesApi::class)
class RealPipelineSwapTest {
    private class Wiring(
        test: TestScope,
    ) {
        val m1 = TestModels.artifact("m1")
        val m2 = TestModels.artifact("m2")
        val repository = FakeModelRepository(initial = listOf(m1, m2), active = m1.manifest.version)
        val source = FakeFrameSource()
        val engine = FakeInferenceEngine(StandardTestDispatcher(test.testScheduler))
        private val clock = FakeClock { test.testScheduler.currentTime * 1_000_000L }
        val recorded = mutableListOf<TelemetryEvent>()
        private val record: (TelemetryEvent) -> Unit = { recorded += it }
        val pipeline =
            InferencePipeline(
                source,
                engine,
                FakeThermalMonitor(),
                FakeMemoryPressureMonitor(),
                clock,
                RecordingTracer(),
                MetricsAggregator(clock, {}),
                record,
                ReconnectPolicy(ReconnectConfig(100.milliseconds, 2.seconds, 0.0), Random(1)),
                test.backgroundScope,
            )
        val start = StartPipeline(pipeline, { repository.active() }, FakeDeviceInfoProvider(), record)
        val activate = ActivateModel(pipeline, repository, FakeAuditLog(), record, clock)
    }

    @Test
    fun `activation is accepted without signal`() =
        runTest {
            val w = Wiring(this)
            w.start()
            w.source.connect(SessionId(1))
            w.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()

            val result = w.activate(w.m2.manifest.version)

            assertEquals(ModelChangeResult.Changed(w.m1.manifest.version, w.m2.manifest.version), result)
            assertEquals(w.m2.manifest.version, w.engine.loadedVersion)
        }

    @Test
    fun `activation is refused while a session is connected`() =
        runTest {
            val w = Wiring(this)
            w.start()
            w.source.connect(SessionId(1))
            runCurrent()

            val result = w.activate(w.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.SWAP_REFUSED), result)
            assertEquals(w.m1, w.repository.active())
        }

    @Test
    fun `a start racing an activation runs on the model the repository reports`() =
        runTest {
            val w = Wiring(this)
            val activation = launch { w.activate(w.m2.manifest.version) }
            val start = launch { assertEquals(StartResult.Started, w.start()) }

            activation.join()
            start.join()

            val active =
                w.repository
                    .active()
                    ?.manifest
                    ?.version
            assertEquals(w.m2.manifest.version, active)
            assertEquals(active, w.engine.loadedVersion)
            assertEquals(active, w.pipeline.state.value.model)
        }

    @Test
    fun `a failing commit leaves engine and repository on the same model`() =
        runTest {
            val w = Wiring(this)
            w.start()
            w.pipeline.stop()
            w.repository.failNext(Operation.ACTIVATE, java.io.IOException("disk full"))

            val result = w.activate(w.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.IO_ERROR), result)
            assertEquals(
                w.repository
                    .active()
                    ?.manifest
                    ?.version,
                w.engine.loadedVersion,
            )
            assertEquals(PipelineState.Idle, w.pipeline.state.value.state)
        }
}
