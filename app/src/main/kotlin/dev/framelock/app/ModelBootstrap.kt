package dev.framelock.app

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.application.ImportResult
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource

enum class BootstrapOutcome {
    /** A model is already active. */
    NOT_NEEDED,

    /** This build carries no package (every release build). */
    NO_PACKAGE,

    /** The package was imported and activated. */
    ACTIVATED,
}

sealed interface BootstrapResult {
    data class Done(
        val outcome: BootstrapOutcome,
    ) : BootstrapResult

    /** Import or activation was refused: the app starts without a model. */
    data class Refused(
        val reason: ModelRejectionReason,
    ) : BootstrapResult
}

/**
 * Debug builds bundle one signed package (`scripts/fetch-model.sh`). When no model is active, it goes through the same
 * use cases as an import from the Models screen, so it is verified, audited and recorded like any other (ADR-003,
 * amendment M5). Release builds bundle nothing.
 */
internal class ModelBootstrap(
    private val hasActiveModel: () -> Boolean,
    private val bundledPackage: () -> ModelSource?,
    private val import: suspend (ModelSource) -> ImportResult,
    private val activate: suspend (ModelVersion) -> ModelChangeResult,
) {
    suspend fun run(): BootstrapResult {
        if (hasActiveModel()) return BootstrapResult.Done(BootstrapOutcome.NOT_NEEDED)
        val source = bundledPackage() ?: return BootstrapResult.Done(BootstrapOutcome.NO_PACKAGE)
        return when (val imported = import(source)) {
            is ImportResult.Refused -> {
                BootstrapResult.Refused(imported.reason)
            }

            is ImportResult.Imported -> {
                when (val activated = activate(imported.manifest.version)) {
                    is ModelChangeResult.Refused -> {
                        BootstrapResult.Refused(activated.reason)
                    }

                    is ModelChangeResult.Changed, is ModelChangeResult.Unchanged -> {
                        BootstrapResult.Done(BootstrapOutcome.ACTIVATED)
                    }
                }
            }
        }
    }
}
