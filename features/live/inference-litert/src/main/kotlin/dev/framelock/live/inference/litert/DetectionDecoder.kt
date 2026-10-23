package dev.framelock.live.inference.litert

import dev.framelock.live.domain.inference.Detection
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.inference.NormalizedRect

/**
 * Turns the four outputs of a TFLite detection post-processing op into domain detections: boxes as
 * (top, left, bottom, right) in 0..1, class indices, scores, and a count. Boxes are clipped into the image.
 */
internal class DetectionDecoder(
    private val minScore: Float,
) {
    fun decode(
        boxes: FloatArray,
        classes: FloatArray,
        scores: FloatArray,
        count: Int,
    ): List<Detection> {
        val usable = minOf(count, classes.size, scores.size, boxes.size / BOX_VALUES)
        return (0 until usable)
            .filter { scores[it] >= minScore }
            .map { index ->
                val box = index * BOX_VALUES
                Detection(
                    box = NormalizedRect.clamped(boxes[box + 1], boxes[box], boxes[box + 3], boxes[box + 2]),
                    label = LabelId(classes[index].toInt().coerceAtLeast(0)),
                    score = scores[index].coerceIn(0f, 1f),
                )
            }
    }

    private companion object {
        const val BOX_VALUES = 4
    }
}
