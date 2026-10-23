package dev.framelock.telemetry.domain.port

import dev.framelock.kernel.telemetry.TelemetryEvent

/** Where accepted events go: a buffer and an uploader (M6). Only the telemetry feature writes to it. */
interface TelemetrySink {
    fun record(event: TelemetryEvent)
}
