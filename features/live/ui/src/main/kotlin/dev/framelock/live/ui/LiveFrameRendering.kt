package dev.framelock.live.ui

import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.Tracer
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.DisplaySlot
import dev.framelock.live.application.usecase.ReportFrameRendered
import dev.framelock.live.domain.inference.LabelId

/** Rendering owns its reusable buffers and reports each displayed frame once (ADR-010, ADR-011). */
class LiveFrameRendering(
    display: DisplaySlot,
    tracer: Tracer,
    private val clock: Clock,
    private val report: ReportFrameRendered,
    val labelOf: (LabelId) -> String,
) {
    private val renderer = FrameRenderer(display, tracer)
    val frames = FrameCallbacks(renderer::bitmapFor, labelOf, ::onDrawn)
    private var lastReported: AnnotatedFrame? = null

    private fun onDrawn(frame: AnnotatedFrame) {
        if (frame == lastReported) return
        lastReported = frame
        report(frame.frameId, frame.sessionId, clock.nowNanos())
    }
}
