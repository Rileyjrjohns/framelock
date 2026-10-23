package dev.framelock.core.application.metrics

/**
 * Debug HUD values, refreshed by [MetricsAggregator.tick] (once per second by the pipeline). Never sent as
 * telemetry (ADR-004).
 *
 * The rates cover the interval since the previous tick. [inferenceMs] and [e2eMs] are the last values seen, null
 * until there is one. [droppedFrames] and [dropsByReason] are cumulative since the aggregator was created;
 * [dropsByReason] only has the reasons that occurred, so its values add up to [droppedFrames].
 */
data class LiveMetrics(
    val inputFps: Double,
    val inferenceFps: Double,
    val inferenceMs: Double?,
    val e2eMs: Double?,
    val droppedFrames: Long,
    val dropsByReason: Map<DropReason, Long>,
) {
    companion object {
        val EMPTY =
            LiveMetrics(
                inputFps = 0.0,
                inferenceFps = 0.0,
                inferenceMs = null,
                e2eMs = null,
                droppedFrames = 0,
                dropsByReason = emptyMap(),
            )
    }
}
