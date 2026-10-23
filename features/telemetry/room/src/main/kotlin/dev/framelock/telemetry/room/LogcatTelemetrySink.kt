package dev.framelock.telemetry.room

import android.util.Log
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.telemetry.domain.port.TelemetrySink

/**
 * Until M6 (capped Room buffer and batch upload), accepted events are only written to Logcat. Events reaching a sink
 * already passed the allowlist (`TelemetryRecorder`), so they hold no free text and no image.
 */
class LogcatTelemetrySink : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        Log.i(TAG, event.toString())
    }

    private companion object {
        const val TAG = "Telemetry"
    }
}
