package dev.framelock.live.capture.uvc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CaptureModeSelectorTest {
    private val selector = CaptureModeSelector()

    @Test
    fun `YUYV at the target size is chosen when it reaches 30 fps`() {
        val chosen =
            selector.select(
                listOf(
                    mode(CaptureFormat.MJPEG, 1280, 720, 60, 30),
                    mode(CaptureFormat.YUYV, 1280, 720, 30, 10),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.YUYV, 1280, 720, 30), chosen)
    }

    @Test
    fun `YUYV takes the lowest rate that reaches 30 fps`() {
        val chosen = selector.select(listOf(mode(CaptureFormat.YUYV, 1280, 720, 60, 30, 15)))

        assertEquals(CaptureMode(CaptureFormat.YUYV, 1280, 720, 30), chosen)
    }

    @Test
    fun `MJPEG at its highest rate is chosen when YUYV stays below 30 fps`() {
        val chosen =
            selector.select(
                listOf(
                    mode(CaptureFormat.YUYV, 1280, 720, 10, 5),
                    mode(CaptureFormat.MJPEG, 1280, 720, 30, 60),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.MJPEG, 1280, 720, 60), chosen)
    }

    @Test
    fun `the size decides before the format`() {
        val chosen =
            selector.select(
                listOf(
                    mode(CaptureFormat.YUYV, 1920, 1080, 30),
                    mode(CaptureFormat.MJPEG, 1280, 720, 30),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.MJPEG, 1280, 720, 30), chosen)
    }

    @Test
    fun `without the target size the closest area wins`() {
        val chosen =
            selector.select(
                listOf(
                    mode(CaptureFormat.MJPEG, 1920, 1080, 30),
                    mode(CaptureFormat.MJPEG, 640, 480, 30),
                    mode(CaptureFormat.MJPEG, 1024, 768, 30),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.MJPEG, 1024, 768, 30), chosen)
    }

    @Test
    fun `a tie on area goes to the larger size`() {
        val chosen =
            selector.select(
                listOf(
                    mode(CaptureFormat.MJPEG, 1000, 600, 30),
                    mode(CaptureFormat.MJPEG, 1000, 840, 30),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.MJPEG, 1000, 840, 30), chosen)
    }

    @Test
    fun `the target size is a constructor parameter`() {
        val chosen =
            CaptureModeSelector(targetWidth = 1920, targetHeight = 1080).select(
                listOf(
                    mode(CaptureFormat.MJPEG, 1280, 720, 30),
                    mode(CaptureFormat.MJPEG, 1920, 1080, 30),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.MJPEG, 1920, 1080, 30), chosen)
    }

    @Test
    fun `YUYV below 30 fps is used when it is the only format`() {
        val chosen = selector.select(listOf(mode(CaptureFormat.YUYV, 1280, 720, 5, 15)))

        assertEquals(CaptureMode(CaptureFormat.YUYV, 1280, 720, 15), chosen)
    }

    @Test
    fun `modes without a rate are ignored`() {
        val chosen =
            selector.select(
                listOf(
                    mode(CaptureFormat.MJPEG, 1280, 720),
                    mode(CaptureFormat.MJPEG, 640, 480, 30),
                ),
            )

        assertEquals(CaptureMode(CaptureFormat.MJPEG, 640, 480, 30), chosen)
    }

    @Test
    fun `no mode gives no choice`() {
        assertNull(selector.select(emptyList()))
    }

    @Test
    fun `an empty target size is rejected`() {
        assertThrows<IllegalArgumentException> { CaptureModeSelector(targetWidth = 0, targetHeight = 720) }
    }

    private fun mode(
        format: CaptureFormat,
        width: Int,
        height: Int,
        vararg fps: Int,
    ) = SupportedMode(format, width, height, fps.toList())
}
