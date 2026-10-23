package dev.framelock.live.domain.telemetry

import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.domain.system.ThermalLevel

/** Events of the live feature (ADR-004). */
sealed interface LiveTelemetry : TelemetryEvent {
    /** One 10 s window. Latency fields are null when the window has no sample. */
    data class PipelineWindow(
        val inferenceMsP50: Double?,
        val inferenceMsP95: Double?,
        val e2eMsP50: Double?,
        val e2eMsP95: Double?,
        val inputFps: Double,
        val inferenceFps: Double,
        val droppedFrames: Int,
    ) : LiveTelemetry

    data class DeviceSnapshotRecorded(
        val model: String,
        val osVersion: String,
        val batteryPct: Int,
        val batteryTempC: Float,
        val thermalLevel: ThermalLevel,
    ) : LiveTelemetry

    data class PipelineDegraded(
        val cause: DegradationCause,
    ) : LiveTelemetry
}
