package dev.framelock.app

import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.port.SourceEvent
import dev.framelock.testing.FakeFrameSource
import dev.framelock.testing.TestFrames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectingFrameSourceTest {
    private val external = FakeFrameSource()
    private val uvc = FakeFrameSource()
    private val back = FakeFrameSource()

    private fun selecting(
        hasExternal: Boolean = false,
        hasUvc: Boolean = false,
    ) = SelectingFrameSource(external, uvc, back, hasExternalCamera = { hasExternal }, hasUvcDevice = { hasUvc })

    @Test
    fun `an external camera comes first`() =
        runTest {
            val source = selecting(hasExternal = true, hasUvc = true)

            source.open()

            assertEquals(listOf(1, 0, 0), listOf(external, uvc, back).map { it.openCalls })
        }

    @Test
    fun `an attached UVC device comes before the back camera`() =
        runTest {
            val source = selecting(hasUvc = true)

            source.open()

            assertEquals(listOf(0, 1, 0), listOf(external, uvc, back).map { it.openCalls })
        }

    @Test
    fun `the back camera is the fallback`() =
        runTest {
            val source = selecting()

            source.open()

            assertEquals(listOf(0, 0, 1), listOf(external, uvc, back).map { it.openCalls })
        }

    @Test
    fun `a reconnection reuses the chosen source even if the choice would change`() =
        runTest {
            var hasUvc = true
            val source = SelectingFrameSource(external, uvc, back, { false }, { hasUvc })
            source.open()
            hasUvc = false

            source.open()

            assertEquals(listOf(0, 2, 0), listOf(external, uvc, back).map { it.openCalls })
        }

    @Test
    fun `close closes only the chosen source and the next open chooses again`() =
        runTest {
            var hasUvc = true
            val source = SelectingFrameSource(external, uvc, back, { false }, { hasUvc })
            source.open()

            source.close()
            hasUvc = false
            source.open()

            assertEquals(listOf(0, 1, 0), listOf(external, uvc, back).map { it.closeCalls })
            assertEquals(listOf(0, 1, 1), listOf(external, uvc, back).map { it.openCalls })
        }

    @Test
    fun `events of every source reach the collector`() =
        runTest {
            val source = selecting()
            val received = mutableListOf<SourceEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.events.toList(received) }

            uvc.disconnect(DisconnectReason.SIGNAL_LOST)
            back.connect(SessionId(3))

            assertEquals(
                listOf(
                    SourceEvent.Disconnected(DisconnectReason.SIGNAL_LOST),
                    SourceEvent.Connected(TestFrames.SPEC, SessionId(3)),
                ),
                received,
            )
        }
}
