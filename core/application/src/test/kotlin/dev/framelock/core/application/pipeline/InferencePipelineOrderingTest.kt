package dev.framelock.core.application.pipeline

import dev.framelock.core.application.metrics.DropReason
import dev.framelock.core.domain.frame.DisconnectReason
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.testing.FakeImageBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Rule 3: a frame older than the last processed one, or from a previous session, is dropped. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineOrderingTest {
    @Test
    fun `frame older than the last processed is dropped and released`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.emit(h.frame(5))
            runCurrent()
            val late = h.frame(3)

            h.emit(late)
            runCurrent()

            assertEquals(listOf(1L to 5L), h.engine.inferredFrames)
            assertTrue((late.image as FakeImageBuffer).isClosed)
        }

    @Test
    fun `frame equal to the last processed is dropped`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.emit(h.frame(5))
            runCurrent()

            h.emit(h.frame(5))
            runCurrent()

            assertEquals(listOf(1L to 5L), h.engine.inferredFrames)
        }

    @Test
    fun `frame from a previous session is dropped and released`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected(session = 2)
            val stale = h.frame(10, session = 1)

            h.emit(stale)
            runCurrent()

            assertEquals(emptyList<Pair<Long, Long>>(), h.engine.inferredFrames)
            assertTrue((stale.image as FakeImageBuffer).isClosed)
        }

    @Test
    fun `first frame of a new session is processed although its id restarted`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.emit(h.frame(5))
            runCurrent()

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            h.source.connect(SessionId(2))
            h.emit(h.frame(0, session = 2))
            runCurrent()

            assertEquals(listOf(1L to 5L, 2L to 0L), h.engine.inferredFrames)
        }

    @Test
    fun `late and stale-session drops are counted`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected(session = 2)
            h.emit(h.frame(5, session = 2))
            runCurrent()

            h.emit(h.frame(4, session = 2), h.frame(9, session = 1))
            runCurrent()
            h.metrics.tick()

            val drops = h.metrics.live.value.dropsByReason
            assertEquals(1L, drops[DropReason.LATE])
            assertEquals(1L, drops[DropReason.STALE_SESSION])
        }
}
