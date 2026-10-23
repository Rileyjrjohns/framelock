package dev.framelock.live.application.metrics

/** Why the pipeline closed a frame without analysing it (ADR-013). */
enum class DropReason {
    /** Replaced by a newer frame while waiting (latest frame wins). */
    CONFLATED,

    /** Not newer than a frame already taken for inference. */
    LATE,

    /** From another session, or discarded by a disconnection. */
    STALE_SESSION,

    /** Received while inference is paused (ADR-013). */
    PAUSED,

    /** Held when the session stopped. */
    STOPPED,

    /** The engine failed on it: no result. */
    ENGINE_ERROR,
}
