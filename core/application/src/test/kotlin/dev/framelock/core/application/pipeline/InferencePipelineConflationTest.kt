package dev.framelock.core.application.pipeline

import dev.framelock.core.application.metrics.DropReason
import dev.framelock.core.domain.system.ThermalLevel
import dev.framelock.core.testing.FakeImageBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Rule 2: latest frame wins; dropped frames are counted and released immediately. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineConflationTest {
    @Test
    fun `frames arriving during an inference are conflated and the latest wins`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) h.emit(h.frame(1), h.frame(2), h.frame(3)) }

            h.emit(h.frame(0))
            runCurrent()

            assertEquals(listOf(1L to 0L, 1L to 3L), h.engine.inferredFrames)
        }

    @Test
    fun `dropped count equals frames replaced during inference`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) h.emit(h.frame(1), h.frame(2), h.frame(3)) }

            h.emit(h.frame(0))
            runCurrent()
            h.metrics.tick()

            assertEquals(2L, h.metrics.live.value.dropsByReason[DropReason.CONFLATED])
        }

    @Test
    fun `a replaced frame's buffer is closed at the moment it is replaced`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            val replaced = h.frame(1)
            var closedBeforeNextInference = false
            h.engine.onInfer = {
                if (it.id.value == 0L) h.emit(replaced, h.frame(2))
                if (it.id.value == 2L) closedBeforeNextInference = (replaced.image as FakeImageBuffer).isClosed
            }

            h.emit(h.frame(0))
            runCurrent()

            assertTrue(closedBeforeNextInference)
        }

    @Test
    fun `nothing is dropped when the engine is faster than the source`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            repeat(10) { id ->
                h.emit(h.frame(id.toLong()))
                runCurrent()
            }
            h.metrics.tick()

            assertEquals(10, h.engine.inferredFrames.size)
            assertEquals(0L, h.metrics.live.value.droppedFrames)
        }

    @Test
    fun `burst of 100 frames with a slow engine infers only first and last`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = { if (it.id.value == 0L) (1L..99L).forEach { id -> h.emit(h.frame(id)) } }

            h.emit(h.frame(0))
            runCurrent()

            assertEquals(listOf(1L to 0L, 1L to 99L), h.engine.inferredFrames)
        }

    @Test
    fun `received equals processed plus dropped, and all buffers released, for a seeded random schedule`() =
        runTest {
            val h = PipelineHarness(this)
            val random = Random(7)
            h.startConnected()
            var nextId = 0L
            var received = 0
            h.engine.onInfer = {
                repeat(random.nextInt(3)) {
                    h.emit(h.frame(nextId++))
                    received++
                }
            }

            repeat(1000) {
                when (random.nextInt(4)) {
                    0 -> {
                        advanceTimeBy(random.nextLong(1, 50))
                    }

                    1 -> {
                        runCurrent()
                    }

                    else -> {
                        h.emit(h.frame(nextId++))
                        received++
                    }
                }
            }
            runCurrent()
            h.pipeline.stop()
            h.metrics.tick()

            val dropped = h.metrics.live.value.droppedFrames
            assertEquals(received.toLong(), h.engine.inferredFrames.size + dropped)
            h.pool.assertAllReleased()
        }

    @Test
    fun `inference starts respect the policy interval`() =
        runTest {
            val h = PipelineHarness(this)
            h.thermal.set(ThermalLevel.SEVERE)
            h.startConnected()
            val starts = mutableListOf<Long>()
            h.engine.onInfer = { starts += testScheduler.currentTime }

            repeat(100) { id ->
                h.emit(h.frame(id.toLong()))
                advanceTimeBy(10)
                runCurrent()
            }

            assertTrue(starts.size >= 5, "starts: $starts")
            assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 100 }, "starts: $starts")
        }

    @Test
    fun `frames arriving during the throttle wait are conflated, released and counted`() =
        runTest {
            val h = PipelineHarness(this)
            h.thermal.set(ThermalLevel.SEVERE)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()
            val waiting = (1L..5L).map { h.frame(it) }

            waiting.forEach {
                h.emit(it)
                advanceTimeBy(10)
                runCurrent()
            }
            advanceTimeBy(200)
            runCurrent()
            h.metrics.tick()

            assertEquals(listOf(1L to 0L, 1L to 5L), h.engine.inferredFrames)
            assertTrue(waiting.dropLast(1).all { (it.image as FakeImageBuffer).isClosed })
            assertEquals(4L, h.metrics.live.value.dropsByReason[DropReason.CONFLATED])
        }
}
