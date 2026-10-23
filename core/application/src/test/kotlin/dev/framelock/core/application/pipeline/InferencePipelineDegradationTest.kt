package dev.framelock.core.application.pipeline

import dev.framelock.core.domain.pipeline.DegradationCause
import dev.framelock.core.domain.pipeline.InferenceRate
import dev.framelock.core.domain.pipeline.PauseCause
import dev.framelock.core.domain.pipeline.PipelineState
import dev.framelock.core.domain.system.MemoryPressure
import dev.framelock.core.domain.system.ThermalLevel
import dev.framelock.core.domain.telemetry.TelemetryEvent
import dev.framelock.core.testing.FakeImageBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Rule 5: under thermal or memory pressure the pipeline lowers its rate or pauses, and the state says why. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineDegradationTest {
    @Test
    fun `thermal SEVERE lowers the rate and state is Degraded(thermal)`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.thermal.set(ThermalLevel.SEVERE)
            runCurrent()

            assertEquals(PipelineState.Degraded(DegradationCause.THERMAL, InferenceRate.capped(10)), h.current.state)
        }

    @Test
    fun `thermal EMERGENCY gives Paused(thermal) and no infer is called`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.thermal.set(ThermalLevel.EMERGENCY)
            runCurrent()
            h.emit(h.frame(0))
            runCurrent()

            assertEquals(PipelineState.Paused(PauseCause.THERMAL), h.current.state)
            assertEquals(emptyList<Pair<Long, Long>>(), h.engine.inferredFrames)
        }

    @Test
    fun `frames received while paused are released immediately`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.thermal.set(ThermalLevel.EMERGENCY)
            runCurrent()
            val frame = h.frame(0)

            h.emit(frame)
            runCurrent()

            assertTrue((frame.image as FakeImageBuffer).isClosed)
        }

    @Test
    fun `returning to NONE resumes at the unlimited rate and state is Running`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.thermal.set(ThermalLevel.EMERGENCY)
            runCurrent()

            h.thermal.set(ThermalLevel.NONE)
            runCurrent()
            h.emit(h.frame(0))
            runCurrent()

            assertEquals(PipelineState.Running(InferenceRate.UNLIMITED), h.current.state)
            assertEquals(listOf(1L to 0L), h.engine.inferredFrames)
        }

    @Test
    fun `memory MODERATE lowers the rate`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.memory.set(MemoryPressure.MODERATE)
            runCurrent()

            assertEquals(PipelineState.Degraded(DegradationCause.MEMORY, InferenceRate.capped(15)), h.current.state)
        }

    @Test
    fun `memory CRITICAL gives Paused(memory)`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.memory.set(MemoryPressure.CRITICAL)
            runCurrent()

            assertEquals(PipelineState.Paused(PauseCause.MEMORY), h.current.state)
        }

    @Test
    fun `combined pressure uses the most restrictive decision and names both causes`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.thermal.set(ThermalLevel.CRITICAL)
            h.memory.set(MemoryPressure.MODERATE)
            runCurrent()
            val mostRestrictive = h.current.state
            h.thermal.set(ThermalLevel.EMERGENCY)
            h.memory.set(MemoryPressure.CRITICAL)
            runCurrent()

            assertEquals(PipelineState.Degraded(DegradationCause.THERMAL, InferenceRate.capped(5)), mostRestrictive)
            assertEquals(PipelineState.Paused(PauseCause.THERMAL_AND_MEMORY), h.current.state)
        }

    @Test
    fun `PipelineDegraded telemetry is emitted once per transition, not per frame`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.thermal.set(ThermalLevel.SEVERE)
            runCurrent()
            repeat(20) { id ->
                h.emit(h.frame(id.toLong()))
                runCurrent()
            }
            h.memory.set(MemoryPressure.CRITICAL)
            runCurrent()

            val degraded = h.telemetry.filterIsInstance<TelemetryEvent.PipelineDegraded>()
            assertEquals(
                listOf(
                    TelemetryEvent.PipelineDegraded(DegradationCause.THERMAL),
                    TelemetryEvent.PipelineDegraded(DegradationCause.MEMORY),
                ),
                degraded,
            )
        }

    @Test
    fun `engine exception degrades the state and the next frame is processed`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.failInferWhen = { it.id.value == 0L }

            h.emit(h.frame(0))
            runCurrent()
            val afterFailure = h.current.state
            h.emit(h.frame(1))
            runCurrent()

            assertEquals(PipelineState.Degraded(DegradationCause.ENGINE_ERROR, InferenceRate.UNLIMITED), afterFailure)
            assertEquals(PipelineState.Running(InferenceRate.UNLIMITED), h.current.state)
            assertEquals(
                1L,
                h.current.annotated
                    ?.frameId
                    ?.value,
            )
        }

    @Test
    fun `monitor flow failing keeps the last known level and does not crash`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.thermal.set(ThermalLevel.SEVERE)
            runCurrent()

            h.thermal.fail(IllegalStateException("thermal service died"))
            runCurrent()
            h.emit(h.frame(0))
            runCurrent()

            assertEquals(PipelineState.Degraded(DegradationCause.THERMAL, InferenceRate.capped(10)), h.current.state)
            assertEquals(listOf(1L to 0L), h.engine.inferredFrames)
        }
}
