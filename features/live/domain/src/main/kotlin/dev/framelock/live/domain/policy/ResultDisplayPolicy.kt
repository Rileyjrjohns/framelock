package dev.framelock.live.domain.policy

import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.inference.InferenceResult

/**
 * A result is displayed only with the frame it was computed from (ADR-001). Frame ids restart in each session, so
 * both the session and the frame id must match.
 */
object ResultDisplayPolicy {
    fun isDisplayable(
        result: InferenceResult,
        frame: Frame,
    ): Boolean = result.sessionId == frame.sessionId && result.frameId == frame.id
}
