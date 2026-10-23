package dev.framelock.live.application

/** Runs on InferenceEngine.dispatcher, not on `limitedParallelism(1)` (ADR-002). */
class Pipeline {
    val note = "limitedParallelism(1) is not used"
}
