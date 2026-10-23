package dev.framelock.core.application.pipeline

import dev.framelock.core.domain.frame.DisconnectReason
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.pipeline.PipelineState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** ADR-001: a result is only ever shown with the frame it was computed from. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineDisplayInvariantTest {
    @Test
    fun `initial state is Idle with no frame`() =
        runTest {
            val h = PipelineHarness(this)

            assertEquals(PipelineState.Idle, h.current.state)
            assertNull(h.current.annotated)
        }

    @Test
    fun `every published AnnotatedFrame pairs a result with its own frame`() =
        runTest {
            val h = PipelineHarness(this)
            val random = Random(11)
            h.startConnected()
            var session = 1L
            var nextId = 0L
            h.engine.onInfer = {
                if (random.nextInt(10) == 0) {
                    h.source.disconnect(DisconnectReason.STREAM_ERROR)
                    session++
                    nextId = 0
                    h.source.connect(SessionId(session))
                }
                if (random.nextBoolean()) h.emit(h.frame(nextId++, session))
            }

            repeat(500) {
                h.emit(h.frame(nextId++, session))
                advanceTimeBy(random.nextLong(1, 40))
                runCurrent()
            }

            val shown = h.states.mapNotNull { it.annotated }
            assertTrue(shown.isNotEmpty())
            assertTrue(
                shown.all { it.result.frameId == it.frameId && it.result.sessionId == it.sessionId },
                "mismatch in ${shown.filterNot {
                    it.result.frameId == it.frameId && it.result.sessionId == it.sessionId
                }}",
            )
        }
}
