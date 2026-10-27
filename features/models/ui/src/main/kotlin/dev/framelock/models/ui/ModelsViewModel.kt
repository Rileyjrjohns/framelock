package dev.framelock.models.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.models.application.ImportResult
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One installed model; [active] is the one the pipeline uses, [previous] the one a rollback returns to. */
data class ModelRow(
    val version: ModelVersion,
    val active: Boolean,
    val previous: Boolean,
)

/** What the last action did, for the status line. */
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

data class ModelsUiState(
    val rows: List<ModelRow> = emptyList(),
    /** An action is running: every control is disabled. */
    val busy: Boolean = false,
    val outcome: ModelsOutcome? = null,
) {
    val canRollBack: Boolean get() = rows.any { it.previous }
}

/**
 * What the Models screen needs. The operations are supplied by `:app` (ADR-014). One action runs at
 * a time; each one refreshes the list afterwards. [canSwap] mirrors `ModelSwapPolicy`: the screen disables every
 * action while it is false, and the use cases refuse anyway.
 */
class ModelsViewModel(
    private val actions: ModelsActions,
    val canSwap: StateFlow<Boolean>,
    /** Wired in `:app` to `TrackUiInteraction` (ADR-004). */
    val track: (ElementId) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModelsUiState())
    val state: StateFlow<ModelsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the list without touching the last outcome. Ignored while an action runs, which refreshes itself. */
    fun refresh() {
        if (mutableState.value.busy) return
        viewModelScope.launch { reload() }
    }

    fun import(source: ModelSource) {
        run {
            when (val result = actions.import(source)) {
                is ImportResult.Imported -> ModelsOutcome.Imported(result.manifest.version)
                is ImportResult.Refused -> ModelsOutcome.Refused(result.reason)
            }
        }
    }

    fun activate(version: ModelVersion) {
        run { actions.activate(version).toOutcome() }
    }

    fun rollback() {
        run { actions.rollback().toOutcome() }
    }

    /** Starts [action] unless another one is running. Called on the main thread, so taps cannot interleave. */
    private fun run(action: suspend () -> ModelsOutcome) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            var outcome: ModelsOutcome? = null
            try {
                outcome =
                    try {
                        action()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Exception,
                    ) {
                        ModelsOutcome.Refused(ModelRejectionReason.IO_ERROR)
                    }
                reload()
            } finally {
                mutableState.update { it.copy(busy = false, outcome = outcome ?: it.outcome) }
            }
        }
    }

    private suspend fun reload() {
        val rows = actions.list()
        mutableState.update { it.copy(rows = rows) }
    }

    private fun ModelChangeResult.toOutcome(): ModelsOutcome =
        when (this) {
            is ModelChangeResult.Changed -> ModelsOutcome.Changed(from, to)
            is ModelChangeResult.Unchanged -> ModelsOutcome.Unchanged(version)
            is ModelChangeResult.Refused -> ModelsOutcome.Refused(reason)
        }
}
