package dev.framelock.models.ui

import androidx.compose.runtime.Immutable
import dev.framelock.kernel.model.ModelVersion

/** One installed model; [active] is the one the pipeline uses, [previous] the one a rollback returns to. */
@Immutable
data class ModelRow(
    val version: ModelVersion,
    val active: Boolean,
    val previous: Boolean,
)
