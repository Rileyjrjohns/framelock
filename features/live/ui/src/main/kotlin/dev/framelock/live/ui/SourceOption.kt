package dev.framelock.live.ui

/**
 * One entry of the video source picker. [id] is opaque to the UI (`:app` maps it to a real source); [label] is what
 * the user reads, which may be a device name read at runtime. Neither ever goes to telemetry (TEL-2).
 */
data class SourceOption(
    val id: String,
    val label: String,
)
