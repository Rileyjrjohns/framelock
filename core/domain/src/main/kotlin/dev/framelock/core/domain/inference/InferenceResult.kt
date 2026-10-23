package dev.framelock.core.domain.inference

import dev.framelock.core.domain.frame.FrameId
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.model.ModelVersion

@JvmInline
value class LabelId(
    val value: Int,
) {
    init {
        require(value >= 0) { "LabelId must be >= 0, was $value" }
    }
}

/** A box in image-relative coordinates: 0 is the left/top edge, 1 the right/bottom edge. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(listOf(left, top, right, bottom).all { it in 0f..1f }) { "Coordinates must be within 0..1: $this" }
        require(left <= right && top <= bottom) { "Edges are inverted: $this" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** For raw model outputs: clips each edge into 0..1 and orders inverted edges. */
        fun clamped(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ): NormalizedRect {
            val l = left.coerceIn(0f, 1f)
            val t = top.coerceIn(0f, 1f)
            val r = right.coerceIn(0f, 1f)
            val b = bottom.coerceIn(0f, 1f)
            return NormalizedRect(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
        }
    }
}

data class Detection(
    val box: NormalizedRect,
    val label: LabelId,
    val score: Float,
) {
    init {
        require(score in 0f..1f) { "Score must be within 0..1, was $score" }
    }
}

/** What the engine computed on one frame, identified by that frame's session and id (ADR-001). */
data class InferenceResult(
    val frameId: FrameId,
    val sessionId: SessionId,
    val modelVersion: ModelVersion,
    val detections: List<Detection>,
    val inferenceNanos: Long,
) {
    init {
        require(inferenceNanos >= 0) { "inferenceNanos must be >= 0, was $inferenceNanos" }
    }
}
