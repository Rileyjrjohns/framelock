package dev.framelock.core.domain.inference

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DetectionTest {
    private val box = NormalizedRect(0f, 0f, 1f, 1f)

    @Test
    fun `score of 0 and 1 is accepted`() {
        assertDoesNotThrow { Detection(box, LabelId(0), 0f) }
        assertDoesNotThrow { Detection(box, LabelId(0), 1f) }
    }

    @ParameterizedTest
    @ValueSource(floats = [-0.01f, 1.01f, Float.NaN])
    fun `score outside 0 to 1 or NaN is rejected`(score: Float) {
        assertThrows<IllegalArgumentException> { Detection(box, LabelId(0), score) }
    }

    @Test
    fun `negative label id is rejected`() {
        assertThrows<IllegalArgumentException> { LabelId(-1) }
    }

    @Test
    fun `negative inferenceNanos is rejected`() {
        assertThrows<IllegalArgumentException> {
            InferenceResult(
                dev.framelock.core.domain.frame
                    .FrameId(0),
                dev.framelock.core.domain.frame.SessionId.FIRST,
                dev.framelock.core.domain.model
                    .ModelVersion("v1"),
                emptyList(),
                inferenceNanos = -1,
            )
        }
    }
}
