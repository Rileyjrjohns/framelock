package dev.framelock.live.domain.policy

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.ImageBuffer
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.Plane
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.InferenceResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultDisplayPolicyTest {
    @Test
    fun `result is displayable with the frame it was computed from`() {
        assertTrue(ResultDisplayPolicy.isDisplayable(result(session = 1, frame = 7), frame(session = 1, frame = 7)))
    }

    @Test
    fun `same frameId but different sessionId is not displayable`() {
        assertFalse(ResultDisplayPolicy.isDisplayable(result(session = 1, frame = 7), frame(session = 2, frame = 7)))
    }

    @Test
    fun `same session, older frameId is not displayable`() {
        // The result describes frame 8; the frame on screen is the older frame 7.
        assertFalse(ResultDisplayPolicy.isDisplayable(result(session = 1, frame = 8), frame(session = 1, frame = 7)))
    }

    @Test
    fun `same session, newer frameId is not displayable`() {
        // The result describes frame 7; the frame on screen is the newer frame 8.
        assertFalse(ResultDisplayPolicy.isDisplayable(result(session = 1, frame = 7), frame(session = 1, frame = 8)))
    }

    @Test
    fun `result from a previous session is not displayable after reconnection`() {
        val beforeReconnection = SessionId.FIRST
        val afterReconnection = beforeReconnection.next()

        // Frame ids restart at 0 in each session, so the same id can legitimately appear in both.
        assertFalse(
            ResultDisplayPolicy.isDisplayable(
                result(session = beforeReconnection.value, frame = 0),
                frame(session = afterReconnection.value, frame = 0),
            ),
        )
    }

    private fun result(
        session: Long,
        frame: Long,
    ) = InferenceResult(
        frameId = FrameId(frame),
        sessionId = SessionId(session),
        modelVersion = ModelVersion("v1"),
        detections = emptyList(),
        inferenceNanos = 1,
    )

    private fun frame(
        session: Long,
        frame: Long,
    ) = Frame(FrameId(frame), SessionId(session), captureTimestampNanos = 0, width = 1, height = 1, image = NoPixels)

    private object NoPixels : ImageBuffer {
        override val format = ImageFormat.YUV_420_888
        override val planes = emptyList<Plane>()

        override fun close() = Unit
    }
}
