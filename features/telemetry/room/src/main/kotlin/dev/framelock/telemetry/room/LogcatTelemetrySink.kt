package dev.framelock.telemetry.room

import android.util.Log
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.telemetry.domain.port.TelemetrySink

/**
 * Writes accepted events to Logcat (tag `Telemetry`). Debug builds tee it next to [RoomTelemetrySink] so a device check
 * can see each event. Events reaching a sink already passed the allowlist (`TelemetryRecorder`), so they hold no free
 * text and no image.
 */
class LogcatTelemetrySink : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        Log.i(TAG, event.toString())
    }

    private companion object {
        const val TAG = "Telemetry"
    }
}
