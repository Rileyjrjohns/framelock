package dev.framelock.models.application

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.pipeline.ModelLoading
import dev.framelock.live.application.pipeline.SwapOutcome
import dev.framelock.models.domain.AuditAction
import dev.framelock.models.domain.AuditEntry
import dev.framelock.models.domain.AuditOutcome
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import dev.framelock.models.domain.policy.ModelSwapPolicy
import dev.framelock.models.domain.policy.SwapDecision
import dev.framelock.models.domain.port.AuditLog
import dev.framelock.models.domain.port.ModelRepository
import dev.framelock.models.domain.telemetry.ModelTelemetry

sealed interface ModelChangeResult {
    /** The requested model is already the active one: nothing was done. */
    data class Unchanged(
        val version: ModelVersion,
    ) : ModelChangeResult

    data class Changed(
        val from: ModelVersion?,
        val to: ModelVersion,
    ) : ModelChangeResult

    data class Refused(
        val reason: ModelRejectionReason,
    ) : ModelChangeResult
}

sealed interface ImportResult {
    data class Imported(
        val manifest: ModelManifest,
    ) : ImportResult

    data class Refused(
        val reason: ModelRejectionReason,
    ) : ImportResult
}

/** Activates an imported model (ADR-003). Telemetry goes through [record], wired in `:app`. */
class ActivateModel(
    pipeline: ModelLoading,
    private val repository: ModelRepository,
    audit: AuditLog,
    record: (TelemetryEvent) -> Unit,
    clock: Clock,
) {
    private val switch = ModelSwitch(pipeline, audit, record, clock)

    suspend operator fun invoke(version: ModelVersion): ModelChangeResult {
        val target = repository.artifact(version)
        val current = repository.active()
        return when {
            !switch.allowed() -> switch.refuse(AuditAction.ACTIVATE, version, ModelRejectionReason.SWAP_REFUSED)
            target == null -> switch.refuse(AuditAction.ACTIVATE, version, ModelRejectionReason.UNKNOWN_VERSION)
            current?.manifest == target.manifest -> ModelChangeResult.Unchanged(version)
            else -> switch.switchTo(AuditAction.ACTIVATE, target, current) { repository.activate(version) }
        }
    }
}

/** Moves back to the previous model, which stays on disk (ADR-003). */
class RollbackModel(
    pipeline: ModelLoading,
    private val repository: ModelRepository,
    audit: AuditLog,
    record: (TelemetryEvent) -> Unit,
    clock: Clock,
) {
    private val switch = ModelSwitch(pipeline, audit, record, clock)

    suspend operator fun invoke(): ModelChangeResult {
        val current = repository.active()
        val previous = repository.previous()
        val currentVersion = current?.manifest?.version
        return when {
            !switch.allowed() -> {
                switch.refuse(AuditAction.ROLLBACK, currentVersion, ModelRejectionReason.SWAP_REFUSED)
            }

            previous == null -> {
                switch.refuse(
                    AuditAction.ROLLBACK,
                    currentVersion,
                    ModelRejectionReason.NO_PREVIOUS_VERSION,
                )
            }

            else -> {
                switch.switchTo(AuditAction.ROLLBACK, previous, current) { repository.rollback() }
            }
        }
    }
}

/** Imports a model from [ModelSource]; the repository verifies hash and signature (ADR-003). Only between sessions. */
class ImportModel(
    pipeline: ModelLoading,
    private val repository: ModelRepository,
    audit: AuditLog,
    record: (TelemetryEvent) -> Unit,
    clock: Clock,
) {
    private val switch = ModelSwitch(pipeline, audit, record, clock)

    suspend operator fun invoke(source: ModelSource): ImportResult {
        if (!switch.allowed()) {
            return ImportResult.Refused(
                switch.refuse(AuditAction.IMPORT, null, ModelRejectionReason.SWAP_REFUSED).reason,
            )
        }
        return repository.import(source).fold(
            onSuccess = { manifest ->
                switch.record(ModelTelemetry.ModelImported(manifest.version))
                switch.audit(AuditAction.IMPORT, manifest.version, AuditOutcome.SUCCEEDED, reason = null)
                ImportResult.Imported(manifest)
            },
            onFailure = { error -> ImportResult.Refused(switch.reject(AuditAction.IMPORT, null, error).reason) },
        )
    }
}

/**
 * What activation and rollback share. The pipeline swaps atomically (ADR-003): it loads the target, lets the repository
 * move its pointer, and loads the previous model back if that fails, all under its lifecycle lock. This class turns
 * each outcome into a result, an audit entry and a telemetry event.
 */
internal class ModelSwitch(
    private val pipeline: ModelLoading,
    private val auditLog: AuditLog,
    val record: (TelemetryEvent) -> Unit,
    private val clock: Clock,
) {
    fun allowed(): Boolean = ModelSwapPolicy.decide(pipeline.state.value.state) == SwapDecision.Allowed

    suspend fun switchTo(
        action: AuditAction,
        target: ModelArtifact,
        previous: ModelArtifact?,
        commit: suspend () -> Result<Unit>,
    ): ModelChangeResult {
        val version = target.manifest.version
        return when (val outcome = pipeline.swapModel(target, previous, commit)) {
            SwapOutcome.Swapped -> {
                record(ModelTelemetry.ModelSwapped(previous?.manifest?.version, version))
                audit(action, version, AuditOutcome.SUCCEEDED, reason = null)
                ModelChangeResult.Changed(previous?.manifest?.version, version)
            }

            SwapOutcome.SessionActive -> {
                refuse(action, version, ModelRejectionReason.SWAP_REFUSED)
            }

            is SwapOutcome.LoadFailed -> {
                fail(action, version, ModelRejectionReason.LOAD_FAILED)
            }

            is SwapOutcome.CommitFailed -> {
                val result = reject(action, version, outcome.cause)
                // The engine still holds the target while the repository kept the previous one: say so.
                if (!outcome.restored) fail(action, previous?.manifest?.version, ModelRejectionReason.LOAD_FAILED)
                result
            }
        }
    }

    fun refuse(
        action: AuditAction,
        version: ModelVersion?,
        reason: ModelRejectionReason,
    ): ModelChangeResult.Refused = conclude(action, version, AuditOutcome.REJECTED, reason)

    /** A typed rejection keeps its reason and is REJECTED; anything else is a FAILED I/O error. */
    fun reject(
        action: AuditAction,
        version: ModelVersion?,
        error: Throwable,
    ): ModelChangeResult.Refused =
        if (error is ModelRejectedException) {
            conclude(action, version, AuditOutcome.REJECTED, error.reason)
        } else {
            fail(action, version, ModelRejectionReason.IO_ERROR)
        }

    private fun fail(
        action: AuditAction,
        version: ModelVersion?,
        reason: ModelRejectionReason,
    ): ModelChangeResult.Refused = conclude(action, version, AuditOutcome.FAILED, reason)

    private fun conclude(
        action: AuditAction,
        version: ModelVersion?,
        outcome: AuditOutcome,
        reason: ModelRejectionReason,
    ): ModelChangeResult.Refused {
        record(ModelTelemetry.ModelRejected(reason))
        audit(action, version, outcome, reason)
        return ModelChangeResult.Refused(reason)
    }

    fun audit(
        action: AuditAction,
        version: ModelVersion?,
        outcome: AuditOutcome,
        reason: ModelRejectionReason?,
    ) = auditLog.record(AuditEntry(action, version, outcome, reason, clock.nowNanos()))
}
