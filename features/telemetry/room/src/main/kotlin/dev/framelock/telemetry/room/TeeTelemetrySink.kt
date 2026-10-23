package dev.framelock.telemetry.room

import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.telemetry.domain.port.TelemetrySink

/** Sends each event to every sink, so debug builds keep the Logcat line next to the stored event. */
class TeeTelemetrySink(
    private vararg val sinks: TelemetrySink,
) : TelemetrySink {
    /** Every sink gets the event; the first failure is rethrown afterwards (the recorder counts it). */
    override fun record(event: TelemetryEvent) {
        var failure: Exception? = null
        for (sink in sinks) {
            try {
                sink.record(event)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
    }
}
