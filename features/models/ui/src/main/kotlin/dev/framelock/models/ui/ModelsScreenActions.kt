package dev.framelock.models.ui

import dev.framelock.kernel.model.ModelVersion

class ModelsScreenActions(
    val onBack: () -> Unit,
    val onActivate: (ModelVersion) -> Unit,
    val onRollback: () -> Unit,
    val onImport: () -> Unit,
)
