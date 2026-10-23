package dev.framelock.models.domain.telemetry

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.models.domain.ModelRejectionReason

/** Events of the models feature (ADR-004). */
sealed interface ModelTelemetry : TelemetryEvent {
    data class ModelSwapped(
        val from: ModelVersion?,
        val to: ModelVersion,
    ) : ModelTelemetry

    data class ModelImported(
        val version: ModelVersion,
    ) : ModelTelemetry

    data class ModelRejected(
        val reason: ModelRejectionReason,
    ) : ModelTelemetry
}
