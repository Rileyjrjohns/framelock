package dev.framelock.models.domain.port

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.domain.AuditEntry
import dev.framelock.models.domain.ModelSource

interface ModelRepository {
    fun active(): ModelArtifact?

    fun previous(): ModelArtifact?

    fun artifact(version: ModelVersion): ModelArtifact?

    fun list(): List<ModelManifest>

    suspend fun import(source: ModelSource): Result<ModelManifest>

    suspend fun activate(version: ModelVersion): Result<Unit>

    suspend fun rollback(): Result<Unit>
}

interface AuditLog {
    fun record(entry: AuditEntry)
}
