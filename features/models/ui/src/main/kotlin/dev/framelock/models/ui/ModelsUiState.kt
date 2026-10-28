package dev.framelock.models.ui

import androidx.compose.runtime.Immutable

@Immutable
data class ModelsUiState(
    val rows: List<ModelRow> = emptyList(),
    /** An action is running: every control is disabled. */
    val busy: Boolean = false,
    val outcome: ModelsOutcome? = null,
    val canSwap: Boolean = true,
) {
    val canRollBack: Boolean get() = rows.any { it.previous }
}
