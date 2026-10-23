package dev.framelock.live.capture.camerax

import dev.framelock.live.domain.frame.TimestampSource
import kotlin.math.abs

/**
 * ADR-011: camera timestamps are on the realtime base on most devices, but not all (`SENSOR_INFO_TIMESTAMP_SOURCE`).
 * Decided once per session from the first frame: a timestamp within one second of `elapsedRealtimeNanos` is taken
 * as realtime; otherwise frames are stamped with their arrival time.
 */
internal object TimestampBase {
    private const val TOLERANCE_NANOS = 1_000_000_000L

    fun of(
        sensorNanos: Long,
        realtimeNanos: Long,
    ): TimestampSource =
        if (abs(realtimeNanos - sensorNanos) < TOLERANCE_NANOS) TimestampSource.REALTIME else TimestampSource.ARRIVAL
}
