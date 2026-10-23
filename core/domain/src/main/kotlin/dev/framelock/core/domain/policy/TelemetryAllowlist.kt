package dev.framelock.core.domain.policy

import dev.framelock.core.domain.telemetry.TelemetryEvent

sealed interface AllowlistVerdict {
    data object Accepted : AllowlistVerdict

    data class Rejected(
        val reason: AllowlistRejection,
    ) : AllowlistVerdict
}

enum class AllowlistRejection {
    NON_FINITE_METRIC,
    NEGATIVE_VALUE,
    PERCENTILE_ORDER,
    OUT_OF_RANGE,
    UNBOUNDED_TEXT,
}

/**
 * Last check before an event leaves the device (ADR-004). Only the enumerated [TelemetryEvent] types pass, and the
 * `when` below has no `else`, so a new type must be given its rules here before the code compiles. Types carry only
 * primitives, enums and bounded tokens; this policy also rejects values a metrics pipeline should never produce.
 */
object TelemetryAllowlist {
    private const val MAX_BATTERY_PCT = 100
    private val BATTERY_PCT_RANGE = 0..MAX_BATTERY_PCT

    /** The only free-form strings in an event: a device model or OS version (ADR-004). */
    private val BOUNDED_TOKEN = Regex("[A-Za-z0-9._ -]{1,64}")

    fun verdict(event: TelemetryEvent): AllowlistVerdict =
        when (event) {
            TelemetryEvent.AppSession -> AllowlistVerdict.Accepted
            is TelemetryEvent.ScreenViewed -> AllowlistVerdict.Accepted
            is TelemetryEvent.UiInteraction -> AllowlistVerdict.Accepted
            is TelemetryEvent.PipelineWindow -> windowVerdict(event)
            is TelemetryEvent.DeviceSnapshotRecorded -> snapshotVerdict(event)
            is TelemetryEvent.ModelSwapped -> AllowlistVerdict.Accepted
            is TelemetryEvent.ModelRejected -> AllowlistVerdict.Accepted
            is TelemetryEvent.PipelineDegraded -> AllowlistVerdict.Accepted
        }

    private fun windowVerdict(window: TelemetryEvent.PipelineWindow): AllowlistVerdict {
        val metrics =
            listOfNotNull(window.inferenceMsP50, window.inferenceMsP95, window.e2eMsP50, window.e2eMsP95) +
                window.inputFps +
                window.inferenceFps
        val percentilesInverted =
            isInverted(window.inferenceMsP50, window.inferenceMsP95) || isInverted(window.e2eMsP50, window.e2eMsP95)
        // Order matters: -Infinity is both non-finite and negative, and is reported as non-finite.
        return when {
            metrics.any { !it.isFinite() } -> reject(AllowlistRejection.NON_FINITE_METRIC)
            metrics.any { it < 0.0 } || window.droppedFrames < 0 -> reject(AllowlistRejection.NEGATIVE_VALUE)
            percentilesInverted -> reject(AllowlistRejection.PERCENTILE_ORDER)
            else -> AllowlistVerdict.Accepted
        }
    }

    private fun snapshotVerdict(snapshot: TelemetryEvent.DeviceSnapshotRecorded): AllowlistVerdict {
        val textsAreBounded = BOUNDED_TOKEN.matches(snapshot.model) && BOUNDED_TOKEN.matches(snapshot.osVersion)
        return when {
            snapshot.batteryPct !in BATTERY_PCT_RANGE -> reject(AllowlistRejection.OUT_OF_RANGE)
            !snapshot.batteryTempC.isFinite() -> reject(AllowlistRejection.NON_FINITE_METRIC)
            !textsAreBounded -> reject(AllowlistRejection.UNBOUNDED_TEXT)
            else -> AllowlistVerdict.Accepted
        }
    }

    /** A 95th percentile below the median cannot come from one sample set. A missing side cannot be compared. */
    private fun isInverted(
        p50: Double?,
        p95: Double?,
    ): Boolean = p50 != null && p95 != null && p95 < p50

    private fun reject(reason: AllowlistRejection) = AllowlistVerdict.Rejected(reason)
}
