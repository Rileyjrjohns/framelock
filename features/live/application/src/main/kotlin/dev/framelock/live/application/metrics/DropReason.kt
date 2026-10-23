package dev.framelock.live.application.metrics

/** Why the pipeline closed a frame without analysing it (ADR-013). */
enum class DropReason {
    /** A newer frame arrived while an inference was running (latest frame wins). */
    CONFLATED,

    /** The frame is older than the last one processed. */
    LATE,

    /** The frame belongs to a previous session. */
    STALE_SESSION,

    /** The pipeline is paused (thermal or memory pressure). */
    PAUSED,

    /** The pipeline is stopping or stopped. */
    STOPPED,
}
