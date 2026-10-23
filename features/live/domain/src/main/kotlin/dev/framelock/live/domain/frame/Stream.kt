package dev.framelock.live.domain.frame

/** Where a frame's capture timestamp comes from (ADR-011). */
enum class TimestampSource {
    /** The source's own timestamp, on the realtime base (`elapsedRealtimeNanos`). */
    REALTIME,

    /** Arrival time read from the `Clock` port, when the source timestamp is on another base. */
    ARRIVAL,
}

data class StreamSpec(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val timestampSource: TimestampSource,
)

enum class DisconnectReason { DEVICE_DETACHED, STREAM_ERROR, PERMISSION_DENIED, OPEN_FAILED }
