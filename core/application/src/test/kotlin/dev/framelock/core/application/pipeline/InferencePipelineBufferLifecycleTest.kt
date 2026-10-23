package dev.framelock.core.application.pipeline

import dev.framelock.core.domain.frame.DisconnectReason
import dev.framelock.core.domain.system.ThermalLevel
import dev.framelock.core.testing.FakeImageBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Rule 6 and ADR-010: every buffer is closed exactly once, by whoever owns it at that moment. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineBufferLifecycleTest {
    @Test
    fun `every received buffer is closed exactly once after stop`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value % 3 == 0L) h.emit(h.frame(it.id.value + 1), h.frame(it.id.value + 2)) }
            for (id in 0L..30L step 3) {
                h.emit(h.frame(id))
                runCurrent()
            }
            h.emit(h.frame(1, session = 9))
            runCurrent()

            h.pipeline.stop()

            h.pool.assertAllReleased()
        }

    @Test
    fun `buffer is released when infer throws`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.failInferWhen = { true }
            val frame = h.frame(0)

            h.emit(frame)
            runCurrent()

            assertTrue((frame.image as FakeImageBuffer).isClosed)
        }

    @Test
    fun `buffer is released when cancelled during the throttle wait`() =
        runTest {
            val h = PipelineHarness(this)
            h.thermal.set(ThermalLevel.SEVERE)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()
            val waiting = h.frame(1)
            h.emit(waiting)
            advanceTimeBy(10)
            runCurrent()

            h.pipeline.stop()

            assertTrue((waiting.image as FakeImageBuffer).isClosed)
            h.pool.assertAllReleased()
        }

    @Test
    fun `pending slot buffer is released on stop`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) h.emit(h.frame(1)) }
            h.emit(h.frame(0))

            h.pipeline.stop()
            runCurrent()

            h.pool.assertAllReleased()
        }

    @Test
    fun `displayed frame buffer is released when replaced by the next annotated frame`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            val first = h.frame(0)
            h.emit(first)
            runCurrent()
            val displayedBeforeNext = !(first.image as FakeImageBuffer).isClosed

            h.emit(h.frame(1))
            runCurrent()

            assertTrue(displayedBeforeNext)
            assertTrue((first.image as FakeImageBuffer).isClosed)
        }

    @Test
    fun `displayed frame buffer is released when cleared on disconnect`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            val shown = h.frame(0)
            h.emit(shown)
            runCurrent()

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()

            assertTrue((shown.image as FakeImageBuffer).isClosed)
            h.pipeline.display.read { assertTrue(it == null) }
        }

    @Test
    fun `display slot never exposes a closed buffer`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            repeat(20) { id ->
                h.emit(h.frame(id.toLong()))
                runCurrent()
                h.pipeline.display.read { frame -> assertFalse((frame?.image as FakeImageBuffer).isClosed) }
            }
        }
}
