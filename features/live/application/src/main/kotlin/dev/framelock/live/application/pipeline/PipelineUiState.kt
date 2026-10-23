package dev.framelock.live.application.pipeline

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.InferenceResult
import dev.framelock.live.domain.pipeline.PipelineState

/** The analysed frame's identity and its result. Pixels are read through [DisplaySlot] only (ADR-010). */
data class AnnotatedFrame(
    val frameId: FrameId,
    val sessionId: SessionId,
    val captureTimestampNanos: Long,
    val width: Int,
    val height: Int,
    val result: InferenceResult,
)

data class PipelineUiState(
    val state: PipelineState,
    val annotated: AnnotatedFrame?,
    val model: ModelVersion?,
) {
    companion object {
        val INITIAL = PipelineUiState(PipelineState.Idle, annotated = null, model = null)
    }
}
