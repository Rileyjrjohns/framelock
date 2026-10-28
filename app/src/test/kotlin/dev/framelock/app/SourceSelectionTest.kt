package dev.framelock.app

import dev.framelock.live.capture.uvc.UvcDeviceInfo
import dev.framelock.live.ui.SourceOption
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceSelectionTest {
    private val labels =
        SourceLabels(auto = "Auto", back = "Back", front = "Front", external = "External", file = "Test video")
    private val webcam = UvcDeviceInfo("046d:085b", "Test Webcam")
    private var attached = listOf(webcam)

    private val selection =
        SourceSelection(labels) {
            buildSourceOptions(labels, hasBack = true, hasFront = true, hasExternal = false, uvcDevices = attached)
        }

    @Test
    fun `options list Auto, the cameras present, then one entry per UVC device`() {
        val options =
            buildSourceOptions(
                labels,
                hasBack = true,
                hasFront = true,
                hasExternal = true,
                uvcDevices = listOf(webcam, UvcDeviceInfo("1234:0001#2", "USB video device")),
            )

        assertEquals(
            listOf(
                SourceOption("auto", "Auto"),
                SourceOption("back", "Back"),
                SourceOption("front", "Front"),
                SourceOption("external", "External"),
                SourceOption("uvc:046d:085b", "Test Webcam"),
                SourceOption("uvc:1234:0001#2", "USB video device"),
            ),
            options,
        )
    }

    @Test
    fun `the test video is listed last, only when present`() {
        val without = buildSourceOptions(labels, hasBack = true, hasFront = false, hasExternal = false, listOf(webcam))
        val with =
            buildSourceOptions(
                labels,
                hasBack = true,
                hasFront = false,
                hasExternal = false,
                uvcDevices = listOf(webcam),
                hasTestVideo = true,
            )

        assertEquals(without + SourceOption("file", "Test video"), with)
        assertEquals(SourceChoice.File, choiceOf("file"))
    }

    @Test
    fun `absent cameras are not listed`() {
        val options = buildSourceOptions(labels, hasBack = false, hasFront = false, hasExternal = false, emptyList())

        assertEquals(listOf(SourceOption("auto", "Auto")), options)
    }

    @Test
    fun `option ids map to choices`() {
        assertEquals(SourceChoice.Auto, choiceOf("auto"))
        assertEquals(SourceChoice.Back, choiceOf("back"))
        assertEquals(SourceChoice.Front, choiceOf("front"))
        assertEquals(SourceChoice.External, choiceOf("external"))
        assertEquals(SourceChoice.Uvc("046d:085b#2"), choiceOf("uvc:046d:085b#2"))
        assertEquals(SourceChoice.Auto, choiceOf("something else"))
    }

    @Test
    fun `locale changes relabel the selection without changing its choice`() =
        runTest {
            selection.options()
            selection.select("back")
            selection.relabel(labels.copy(back = "Caméra arrière"))
            assertEquals(SourceOption("back", "Caméra arrière"), selection.current.value)
            assertEquals(SourceChoice.Back, selection.choice)
            selection.select("uvc:046d:085b")
            selection.relabel(labels.copy(back = "Caméra arrière"))
            assertEquals("Test Webcam", selection.current.value.label)
        }

    @Test
    fun `Auto is selected by default`() {
        assertEquals(SourceOption("auto", "Auto"), selection.current.value)
        assertEquals(SourceChoice.Auto, selection.choice)
    }

    @Test
    fun `selecting a listed option changes the choice`() =
        runTest {
            selection.options()

            selection.select("uvc:046d:085b")

            assertEquals(SourceOption("uvc:046d:085b", "Test Webcam"), selection.current.value)
            assertEquals(SourceChoice.Uvc("046d:085b"), selection.choice)
        }

    @Test
    fun `an id that was never listed is ignored`() =
        runTest {
            selection.options()

            selection.select("uvc:dead:beef")

            assertEquals(SourceChoice.Auto, selection.choice)
        }

    @Test
    fun `options are listed afresh each time`() =
        runTest {
            assertEquals(4, selection.options().size)

            attached = emptyList()

            assertEquals(3, selection.options().size)
        }
}
