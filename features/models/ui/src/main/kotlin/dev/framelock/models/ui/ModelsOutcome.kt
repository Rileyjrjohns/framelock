package dev.framelock.models.ui

import androidx.compose.runtime.Immutable
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.domain.ModelRejectionReason

/** What the last action did, for the status line. */
@Immutable
sealed interface ModelsOutcome {
    data class Imported(
        val version: ModelVersion,
    ) : ModelsOutcome

    data class Changed(
        val from: ModelVersion?,
        val to: ModelVersion,
    ) : ModelsOutcome

    data class Unchanged(
        val version: ModelVersion,
    ) : ModelsOutcome

    data class Refused(
        val reason: ModelRejectionReason,
    ) : ModelsOutcome
}
