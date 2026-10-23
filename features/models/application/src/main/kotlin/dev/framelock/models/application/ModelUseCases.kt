package dev.framelock.models.application

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.pipeline.ModelLoading
import dev.framelock.live.application.pipeline.SessionActiveException
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
import kotlinx.coroutines.CancellationException

sealed interface ModelChangeResult {
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
        return when {
            !switch.allowed() -> switch.refuse(AuditAction.ACTIVATE, version, ModelRejectionReason.SWAP_REFUSED)
            target == null -> switch.refuse(AuditAction.ACTIVATE, version, ModelRejectionReason.UNKNOWN_VERSION)
            else -> switch.switchTo(AuditAction.ACTIVATE, target, repository.active()) { repository.activate(version) }
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
                switch.audit(AuditAction.IMPORT, manifest.version, AuditOutcome.SUCCEEDED, reason = null)
                ImportResult.Imported(manifest)
            },
            onFailure = { error -> ImportResult.Refused(switch.reject(AuditAction.IMPORT, null, error).reason) },
        )
    }
}

/**
 * The sequence shared by activation and rollback: the engine loads the target first; only then does the repository
 * move its pointer. If the repository fails, the previous model is loaded back, so engine and repository agree.
 */
internal class ModelSwitch(
    private val pipeline: ModelLoading,
    private val auditLog: AuditLog,
    private val record: (TelemetryEvent) -> Unit,
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
        val loadFailure = load(target)
        val commitFailure = if (loadFailure == null) commit().exceptionOrNull() else null
        return when {
            loadFailure != null -> {
                reject(action, version, loadFailure)
            }

            commitFailure != null -> {
                previous?.let { load(it) }
                reject(action, version, commitFailure)
            }

            else -> {
                record(ModelTelemetry.ModelSwapped(previous?.manifest?.version, version))
                audit(action, version, AuditOutcome.SUCCEEDED, reason = null)
                ModelChangeResult.Changed(previous?.manifest?.version, version)
            }
        }
    }

    fun refuse(
        action: AuditAction,
        version: ModelVersion?,
        reason: ModelRejectionReason,
    ): ModelChangeResult.Refused {
        record(ModelTelemetry.ModelRejected(reason))
        audit(action, version, AuditOutcome.REJECTED, reason)
        return ModelChangeResult.Refused(reason)
    }

    /** A typed rejection is REJECTED with its reason; anything else is a FAILED I/O error (or load failure). */
    fun reject(
        action: AuditAction,
        version: ModelVersion?,
        error: Throwable,
    ): ModelChangeResult.Refused {
        val reason =
            when (error) {
                is SessionActiveException -> return refuse(action, version, ModelRejectionReason.SWAP_REFUSED)
                is ModelRejectedException -> error.reason
                is LoadFailure -> ModelRejectionReason.LOAD_FAILED
                else -> ModelRejectionReason.IO_ERROR
            }
        val outcome = if (error is ModelRejectedException) AuditOutcome.REJECTED else AuditOutcome.FAILED
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

    /** Null when loaded; otherwise why not. The engine may fail in any way: it is reported, never thrown. */
    private suspend fun load(model: ModelArtifact): Throwable? =
        try {
            pipeline.loadModel(model)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionActiveException) {
            e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            LoadFailure(e)
        }

    private class LoadFailure(
        cause: Throwable,
    ) : Exception(cause)
}
