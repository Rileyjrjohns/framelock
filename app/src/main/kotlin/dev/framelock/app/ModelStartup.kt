package dev.framelock.app

import android.content.Context
import android.util.Log
import dev.framelock.models.application.ActivateModel
import dev.framelock.models.application.ImportModel
import dev.framelock.models.domain.ModelSource
import dev.framelock.models.store.FileModelRepository
import java.io.InputStream

internal class ModelStartup(
    private val appContext: Context,
    private val modelRepository: FileModelRepository,
    private val activeLabels: ActiveLabels,
    private val importModel: ImportModel,
    private val activateModel: ActivateModel,
) {
    suspend fun run() {
        activeLabels.refresh()
        val bootstrap =
            ModelBootstrap(
                hasActiveModel = { modelRepository.active() != null },
                bundledPackage = ::bundledPackage,
                import = importModel::invoke,
                activate = activateModel::invoke,
            )
        val result = bootstrap.run()
        if (result is BootstrapResult.Refused) Log.w(TAG, "The bundled model was not installed: ${result.reason}")
        activeLabels.refresh()
    }

    private fun bundledPackage(): ModelSource? {
        val name =
            appContext.assets
                .list(BUNDLED_DIRECTORY)
                .orEmpty()
                .firstOrNull { it.endsWith(PACKAGE_SUFFIX) } ?: return null
        return object : ModelSource {
            override val displayName = name

            override fun open(): InputStream = appContext.assets.open("$BUNDLED_DIRECTORY/$name")
        }
    }

    private companion object {
        const val TAG = "ModelStartup"
        const val BUNDLED_DIRECTORY = "models"
        const val PACKAGE_SUFFIX = ".flmodel"
    }
}
