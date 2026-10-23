package dev.framelock.core.domain.telemetry

import dev.framelock.core.domain.model.ModelRejectionReason
import dev.framelock.core.domain.model.ModelVersion
import dev.framelock.core.domain.pipeline.DegradationCause
import dev.framelock.core.domain.system.ThermalLevel

/** Screens that can be tracked. Tracking a new one is a reviewed code change (ADR-004). */
enum class ScreenId { LIVE, MODELS }

/** UI elements that can be tracked. */
enum class ElementId { START, STOP, TOGGLE_HUD, IMPORT_MODEL, ACTIVATE_MODEL, ROLLBACK_MODEL }

/** Metrics-only events: primitives, enums and bounded tokens; never images, never free text (ADR-004). */
sealed interface TelemetryEvent {
    data object AppSession : TelemetryEvent

    data class ScreenViewed(
        val screen: ScreenId,
    ) : TelemetryEvent

    data class UiInteraction(
        val element: ElementId,
    ) : TelemetryEvent

    /** One 10 s window. Latency fields are null when the window has no sample. */
    data class PipelineWindow(
        val inferenceMsP50: Double?,
        val inferenceMsP95: Double?,
        val e2eMsP50: Double?,
        val e2eMsP95: Double?,
        val inputFps: Double,
        val inferenceFps: Double,
        val droppedFrames: Int,
    ) : TelemetryEvent

    data class DeviceSnapshotRecorded(
        val model: String,
        val osVersion: String,
        val batteryPct: Int,
        val batteryTempC: Float,
        val thermalLevel: ThermalLevel,
    ) : TelemetryEvent

    data class ModelSwapped(
        val from: ModelVersion?,
        val to: ModelVersion,
    ) : TelemetryEvent

    data class ModelRejected(
        val reason: ModelRejectionReason,
    ) : TelemetryEvent

    data class PipelineDegraded(
        val cause: DegradationCause,
    ) : TelemetryEvent
}
