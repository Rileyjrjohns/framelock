package dev.framelock.live.application.pipeline

import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.testing.FakeImageBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Rule 4: disconnection, in-flight results, reconnection with ReconnectPolicy. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineConnectionTest {
    @Test
    fun `state goes Idle then Connecting then Running on Connected`() =
        runTest {
            val h = PipelineHarness(this)

            h.startConnected()

            val kinds = h.states.map { it.state::class.simpleName }.distinct()
            assertEquals(listOf("Idle", "Connecting", "Running"), kinds)
        }

    @Test
    fun `on Disconnected, state is NoSignal and result is cleared in the same emission`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()

            assertTrue(h.states.none { it.state is PipelineState.NoSignal && it.annotated != null }, "${h.states}")
            assertEquals(PipelineState.NoSignal(DisconnectReason.DEVICE_DETACHED), h.current.state)
            assertNull(h.current.annotated)
        }

    @Test
    fun `NoSignal reason reflects DisconnectReason`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.source.disconnect(DisconnectReason.STREAM_ERROR)
            runCurrent()

            assertEquals(PipelineState.NoSignal(DisconnectReason.STREAM_ERROR), h.current.state)
        }

    @Test
    fun `in-flight result is discarded when the session ends during inference`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            val inFlight = h.frame(0)
            h.engine.onInfer = { h.source.disconnect(DisconnectReason.DEVICE_DETACHED) }

            h.emit(inFlight)
            runCurrent()

            assertTrue(h.states.none { it.annotated != null }, "${h.states}")
            assertTrue((inFlight.image as FakeImageBuffer).isClosed)
        }

    @Test
    fun `in-flight result is discarded even after reconnecting to a new session`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.engine.onInfer = {
                if (it.sessionId.value == 1L) {
                    h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
                    h.source.connect(SessionId(2))
                }
            }

            h.emit(h.frame(0))
            runCurrent()

            assertTrue(h.states.none { it.annotated != null }, "${h.states}")
            assertTrue(h.current.state is PipelineState.Running)
        }

    @Test
    fun `after reconnection no result is shown until the new session's first result`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.emit(h.frame(0))
            runCurrent()

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            h.source.connect(SessionId(2))
            runCurrent()
            val beforeFirstResult = h.current
            h.emit(h.frame(0, session = 2))
            runCurrent()

            assertNull(beforeFirstResult.annotated)
            assertEquals(SessionId(2), h.current.annotated?.sessionId)
        }

    @Test
    fun `reconnection waits the ReconnectPolicy delays`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.failNextOpens(3)

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()
            val opensAt = mutableListOf<Int>()
            for (delayMs in listOf(100L, 200L, 400L, 800L)) {
                advanceTimeBy(delayMs - 1)
                runCurrent()
                opensAt += h.source.openCalls
                advanceTimeBy(1)
                runCurrent()
                opensAt += h.source.openCalls
            }

            assertEquals(listOf(1, 2, 2, 3, 3, 4, 4, 5), opensAt)
        }

    @Test
    fun `successful reconnection resets the backoff`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.failNextOpens(2)
            h.source.onOpen = { h.source.connect(SessionId(it.toLong() + 1)) }
            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            advanceTimeBy(100 + 200 + 400)
            runCurrent()
            val opensAfterReconnect = h.source.openCalls

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertEquals(opensAfterReconnect + 1, h.source.openCalls)
        }

    @Test
    fun `stop during backoff cancels reconnection`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()
            val opens = h.source.openCalls

            h.pipeline.stop()
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(opens, h.source.openCalls)
            assertEquals(PipelineState.Idle, h.current.state)
        }

    @Test
    fun `new session adopts an incremented SessionId`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            h.source.connect(SessionId(2))
            h.emit(h.frame(0, session = 2))
            runCurrent()

            assertEquals(listOf(2L to 0L), h.engine.inferredFrames)
        }

    @Test
    fun `Connected with a non-increasing SessionId is rejected and its frames dropped`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected(session = 2)
            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)

            h.source.connect(SessionId(2))
            h.emit(h.frame(0, session = 2))
            runCurrent()

            assertEquals(emptyList<Pair<Long, Long>>(), h.engine.inferredFrames)
            assertTrue(h.current.state is PipelineState.NoSignal)
        }

    @Test
    fun `events after stop are ignored and their buffers released`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.pipeline.stop()

            h.emit(h.frame(0), h.frame(1))
            runCurrent()

            assertEquals(emptyList<Pair<Long, Long>>(), h.engine.inferredFrames)
            h.pool.assertAllReleased()
        }
}
