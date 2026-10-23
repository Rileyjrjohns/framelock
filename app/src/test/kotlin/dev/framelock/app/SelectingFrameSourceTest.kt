package dev.framelock.app

import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.port.SourceEvent
import dev.framelock.testing.FakeFrameSource
import dev.framelock.testing.TestFrames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private val LABELS = SourceLabels(auto = "Auto", back = "Back", front = "Front", external = "External")

@OptIn(ExperimentalCoroutinesApi::class)
class SelectingFrameSourceTest {
    private val external = FakeFrameSource()
    private val uvc = FakeFrameSource()
    private val back = FakeFrameSource()
    private val front = FakeFrameSource()
    private val uvcMode = MutableStateFlow<String?>(null)
    private var choice: SourceChoice = SourceChoice.Auto
    private var hasUvc = false

    private fun TestScope.selecting(
        hasExternal: Boolean = false,
        hasUvc: Boolean = false,
    ): SelectingFrameSource {
        this@SelectingFrameSourceTest.hasUvc = hasUvc
        return SelectingFrameSource(
            external,
            uvc,
            back,
            front,
            choice = { choice },
            hasExternalCamera = { hasExternal },
            hasUvcDevice = { this@SelectingFrameSourceTest.hasUvc },
            labels = LABELS,
            uvcDescription = uvcMode,
            scope = backgroundScope,
        )
    }

    private fun opened() = listOf(external, uvc, back, front).map { it.openCalls }

    @Test
    fun `an external camera comes first`() =
        runTest {
            val source = selecting(hasExternal = true, hasUvc = true)

            source.open()

            assertEquals(listOf(1, 0, 0, 0), opened())
        }

    @Test
    fun `an attached UVC device comes before the back camera`() =
        runTest {
            val source = selecting(hasUvc = true)

            source.open()

            assertEquals(listOf(0, 1, 0, 0), opened())
        }

    @Test
    fun `the back camera is the fallback`() =
        runTest {
            val source = selecting()

            source.open()

            assertEquals(listOf(0, 0, 1, 0), opened())
        }

    @Test
    fun `a reconnection reuses the chosen source even if the choice would change`() =
        runTest {
            val source = selecting(hasUvc = true)
            source.open()
            hasUvc = false

            source.open()

            assertEquals(listOf(0, 2, 0, 0), opened())
        }

    @Test
    fun `close closes only the chosen source and the next open chooses again`() =
        runTest {
            val source = selecting(hasUvc = true)
            source.open()

            source.close()
            hasUvc = false
            source.open()

            assertEquals(listOf(0, 1, 0, 0), listOf(external, uvc, back, front).map { it.closeCalls })
            assertEquals(listOf(0, 1, 1, 0), opened())
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

    @Test
    fun `an explicit choice overrides the automatic order`() =
        runTest {
            choice = SourceChoice.Back
            val source = selecting(hasExternal = true, hasUvc = true)

            source.open()

            assertEquals(listOf(0, 0, 1, 0), opened())
        }

    @Test
    fun `each explicit choice opens its own source`() =
        runTest {
            val cases =
                listOf(
                    SourceChoice.External to listOf(1, 0, 0, 0),
                    SourceChoice.Uvc("046d:085b") to listOf(0, 1, 0, 0),
                    SourceChoice.Back to listOf(0, 0, 1, 0),
                    SourceChoice.Front to listOf(0, 0, 0, 1),
                )
            cases.forEach { (explicit, expected) ->
                val before = opened()
                choice = explicit
                val source = selecting()

                source.open()

                assertEquals(expected, opened().zip(before) { now, then -> now - then }, explicit.toString())
            }
        }

    @Test
    fun `a new choice applies at the next open after close, not to a reconnection`() =
        runTest {
            val source = selecting()
            source.open()
            choice = SourceChoice.Front

            source.open()
            assertEquals(listOf(0, 0, 2, 0), opened())

            source.close()
            source.open()
            assertEquals(listOf(0, 0, 2, 1), opened())
        }

    @Test
    fun `the description follows the chosen source`() =
        runTest {
            choice = SourceChoice.Front
            val source = selecting()
            runCurrent()
            assertNull(source.activeDescription.value)

            source.open()
            runCurrent()
            assertEquals("Front", source.activeDescription.value)

            source.close()
            runCurrent()
            assertNull(source.activeDescription.value)
        }

    @Test
    fun `a UVC source shows the description it publishes`() =
        runTest {
            choice = SourceChoice.Uvc("046d:085b")
            val source = selecting()
            source.open()
            runCurrent()
            assertNull(source.activeDescription.value)

            uvcMode.value = "Test Webcam · MJPEG 1280x720@30"
            runCurrent()
            assertEquals("Test Webcam · MJPEG 1280x720@30", source.activeDescription.value)

            uvcMode.value = null
            runCurrent()
            assertNull(source.activeDescription.value)
        }
}
