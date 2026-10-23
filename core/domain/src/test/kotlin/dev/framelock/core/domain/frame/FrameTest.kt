package dev.framelock.core.domain.frame

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FrameTest {
    @Test
    fun `valid frame is accepted`() {
        assertDoesNotThrow { frame(width = 640, height = 480, captureTimestampNanos = 0) }
    }

    @ParameterizedTest
    @CsvSource("0,480", "640,0", "-1,480")
    fun `width and height must be positive`(
        width: Int,
        height: Int,
    ) {
        assertThrows<IllegalArgumentException> { frame(width = width, height = height) }
    }

    @Test
    fun `negative capture timestamp is rejected`() {
        assertThrows<IllegalArgumentException> { frame(captureTimestampNanos = -1) }
    }

    private fun frame(
        width: Int = 640,
        height: Int = 480,
        captureTimestampNanos: Long = 10,
    ) = Frame(FrameId(0), SessionId.FIRST, captureTimestampNanos, width, height, NoPixels)

    private object NoPixels : ImageBuffer {
        override val format = ImageFormat.YUV_420_888
        override val planes = emptyList<Plane>()

        override fun close() = Unit
    }
}
