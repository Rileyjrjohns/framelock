package dev.framelock.app

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.application.ActivateModel
import dev.framelock.models.application.ImportModel
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.application.RollbackModel
import dev.framelock.models.domain.ModelSource
import dev.framelock.models.ui.ModelsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppModelActions(
    private val listing: ModelListing,
    private val labels: ActiveLabels,
    private val importer: ImportModel,
    private val activator: ActivateModel,
    private val rollback: RollbackModel,
) : ModelsActions {
    override suspend fun list() = listing.rows()

    override suspend fun import(source: ModelSource) = importer(source)

    override suspend fun activate(version: ModelVersion) = refreshLabels(activator(version))

    override suspend fun rollback() = refreshLabels(rollback.invoke())

    private suspend fun refreshLabels(result: ModelChangeResult): ModelChangeResult {
        if (result is ModelChangeResult.Changed) withContext(Dispatchers.IO) { labels.refresh() }
        return result
    }
}
