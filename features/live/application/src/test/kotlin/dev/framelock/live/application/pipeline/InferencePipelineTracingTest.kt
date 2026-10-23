package dev.framelock.live.application.pipeline

import dev.framelock.kernel.port.TraceSection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Rule 7 (pipeline part, ADR-009): the INFERENCE section wraps each inference. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineTracingTest {
    @Test
    fun `inference section wraps each infer call`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            val sectionsDuringInfer = mutableListOf<TraceSection?>()
            h.engine.onInfer = { sectionsDuringInfer += h.tracer.currentSection() }

            repeat(3) { id ->
                h.emit(h.frame(id.toLong()))
                runCurrent()
            }

            assertEquals(List(3) { TraceSection.INFERENCE }, sectionsDuringInfer)
            h.tracer.assertBalanced()
        }

    @Test
    fun `sections are balanced when infer throws`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.failInferWhen = { true }

            h.emit(h.frame(0))
            runCurrent()

            h.tracer.assertBalanced()
        }

    @Test
    fun `no section is opened for dropped frames`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) h.emit(h.frame(1), h.frame(2), h.frame(3)) }

            h.emit(h.frame(0))
            runCurrent()

            assertEquals(h.engine.inferredFrames.size, h.tracer.records.count { it.startsWith("begin") })
        }
}
