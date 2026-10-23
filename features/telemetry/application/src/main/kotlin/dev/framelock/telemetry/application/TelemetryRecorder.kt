package dev.framelock.telemetry.application

import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.telemetry.domain.AllowlistVerdict
import dev.framelock.telemetry.domain.TelemetryAllowlist
import dev.framelock.telemetry.domain.port.TelemetrySink

/**
 * The only way events reach the [TelemetrySink]: each one passes [TelemetryAllowlist] first (ADR-004). `:app` wires
 * [record] into every feature as a plain `(TelemetryEvent) -> Unit`, so no feature depends on this one (ADR-014).
 * Telemetry never breaks the app: a failing sink is counted, not propagated.
 */
class TelemetryRecorder(
    private val sink: TelemetrySink,
) {
    private val lock = Any()
    private var rejected = 0L
    private var failed = 0L

    val rejectedCount: Long get() = synchronized(lock) { rejected }
    val failedCount: Long get() = synchronized(lock) { failed }

    fun record(event: TelemetryEvent) {
        if (TelemetryAllowlist.verdict(event) != AllowlistVerdict.Accepted) {
            synchronized(lock) { rejected++ }
            return
        }
        try {
            sink.record(event)
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: RuntimeException,
        ) {
            synchronized(lock) { failed++ }
        }
    }
}

class TrackScreen(
    private val recorder: TelemetryRecorder,
) {
    operator fun invoke(screen: ScreenId) = recorder.record(UsageTelemetry.ScreenViewed(screen))
}

class TrackUiInteraction(
    private val recorder: TelemetryRecorder,
) {
    operator fun invoke(element: ElementId) = recorder.record(UsageTelemetry.UiInteraction(element))
}
