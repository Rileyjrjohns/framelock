package dev.framelock.core.domain.model

import java.io.InputStream

/** Bounded token, safe to send as telemetry (ADR-004). */
@JvmInline
value class ModelVersion(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Model version must match ${PATTERN.pattern}, was '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
    }
}

enum class TensorDataType { FLOAT32, UINT8, INT8 }

data class TensorSpec(
    val shape: List<Int>,
    val dataType: TensorDataType,
) {
    init {
        require(shape.isNotEmpty() && shape.all { it > 0 }) { "Tensor shape must have positive dimensions: $shape" }
    }
}

data class ModelManifest(
    val version: ModelVersion,
    val sha256: String,
    /** Base64 ECDSA P-256 signature over the model file (ADR-003). */
    val signature: String,
    val inputSpec: TensorSpec,
    val outputSpec: List<TensorSpec>,
) {
    init {
        require(SHA256.matches(sha256)) { "sha256 must be 64 lowercase hex chars" }
        require(signature.isNotBlank()) { "signature must not be blank" }
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

/** A model on disk: its manifest and the path of its model file. */
data class ModelArtifact(
    val manifest: ModelManifest,
    val location: String,
)

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

enum class AuditAction { IMPORT, ACTIVATE, ROLLBACK }

enum class AuditOutcome { SUCCEEDED, REJECTED, FAILED }

data class AuditEntry(
    val action: AuditAction,
    val version: ModelVersion?,
    val outcome: AuditOutcome,
    val reason: ModelRejectionReason?,
    val atNanos: Long,
)
