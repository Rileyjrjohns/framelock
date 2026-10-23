package dev.framelock.live.inference.litert

import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.inference.NormalizedRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetectionDecoderTest {
    @Test
    fun `boxes are read top, left, bottom, right and filtered by score`() {
        val decoder = DetectionDecoder(minScore = 0.5f)
        val boxes = floatArrayOf(0.1f, 0.2f, 0.5f, 0.6f, 0f, 0f, 1f, 1f)
        val classes = floatArrayOf(0f, 2f)
        val scores = floatArrayOf(0.9f, 0.3f)

        val detections = decoder.decode(boxes, classes, scores, count = 2)

        assertEquals(1, detections.size)
        assertEquals(NormalizedRect(0.2f, 0.1f, 0.6f, 0.5f), detections.single().box)
        assertEquals(LabelId(0), detections.single().label)
        assertEquals(0.9f, detections.single().score)
    }

    @Test
    fun `boxes outside the image are clipped`() {
        val decoder = DetectionDecoder(minScore = 0.5f)

        val detections =
            decoder.decode(
                floatArrayOf(-0.2f, -0.1f, 1.3f, 1.1f),
                floatArrayOf(1f),
                floatArrayOf(0.8f),
                count = 1,
            )

        assertEquals(NormalizedRect(0f, 0f, 1f, 1f), detections.single().box)
    }

    @Test
    fun `count beyond the arrays is bounded`() {
        val decoder = DetectionDecoder(minScore = 0f)

        val detections = decoder.decode(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0f), floatArrayOf(0.4f), count = 25)

        assertEquals(1, detections.size)
    }
}
