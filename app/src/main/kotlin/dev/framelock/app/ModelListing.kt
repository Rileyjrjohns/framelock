package dev.framelock.app

import dev.framelock.models.store.FileModelRepository
import dev.framelock.models.ui.ModelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class ModelListing(
    private val modelRepository: FileModelRepository,
) {
    suspend fun rows(): List<ModelRow> =
        withContext(Dispatchers.IO) {
            val active = modelRepository.active()?.manifest?.version
            val previous = modelRepository.previous()?.manifest?.version
            modelRepository.list().map { ModelRow(it.version, it.version == active, it.version == previous) }
        }
}
