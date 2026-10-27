package dev.framelock.models.ui

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.application.ImportResult
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.domain.ModelSource

/** Model operations and policy state supplied by the composition root. */
interface ModelsActions {
    suspend fun list(): List<ModelRow>

    suspend fun import(source: ModelSource): ImportResult

    suspend fun activate(version: ModelVersion): ModelChangeResult

    suspend fun rollback(): ModelChangeResult
}
