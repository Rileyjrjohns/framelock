package dev.framelock.models.domain

import dev.framelock.kernel.model.ModelVersion
import java.io.InputStream

/** Where an imported model comes from, without any platform type (ADR-003). */
interface ModelSource {
    val displayName: String

    fun open(): InputStream
}

enum class ModelRejectionReason {
    BAD_HASH,
    BAD_SIGNATURE,
    MALFORMED_MANIFEST,
    IO_ERROR,
    UNKNOWN_VERSION,
    NO_PREVIOUS_VERSION,
    SWAP_REFUSED,
    LOAD_FAILED,
}

/** Thrown (or returned in a failed `Result`) by a [ModelSource] import or a repository when a model is refused. */
class ModelRejectedException(
    val reason: ModelRejectionReason,
) : Exception("Model rejected: $reason")

enum class AuditAction { IMPORT, ACTIVATE, ROLLBACK }

enum class AuditOutcome { SUCCEEDED, REJECTED, FAILED }

data class AuditEntry(
    val action: AuditAction,
    val version: ModelVersion?,
    val outcome: AuditOutcome,
    val reason: ModelRejectionReason?,
    val atNanos: Long,
)
