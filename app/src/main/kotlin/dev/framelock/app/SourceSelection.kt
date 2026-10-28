package dev.framelock.app

import dev.framelock.live.ui.SourceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's source choice, shared by the picker (through the view model) and [SelectingFrameSource]. [listSources]
 * is asked each time the picker opens; the options it returns are remembered so that [select] can find the label of
 * an id. The choice survives the device being unplugged: it is then retried like any lost source.
 *
 * Thread-safe: [current] and [choice] are read from the pipeline's threads while the UI selects.
 */
class SourceSelection(
    labels: SourceLabels,
    private val listSources: suspend () -> List<SourceOption>,
) {
    private val auto = SourceOption(AUTO_ID, labels.auto)
    private val selected = MutableStateFlow(auto)

    @Volatile private var known = listOf(auto)

    val current: StateFlow<SourceOption> = selected.asStateFlow()

    /** What the pipeline should open next. */
    val choice: SourceChoice get() = choiceOf(selected.value.id)

    suspend fun options(): List<SourceOption> = listSources().also { known = it }

    fun relabel(labels: SourceLabels) {
        known = known.map(labels::relabel)
        selected.value = labels.relabel(selected.value)
    }

    /** Selects the option with [id], among those last listed; an id that is not there is ignored. */
    fun select(id: String) {
        known.firstOrNull { it.id == id }?.let { selected.value = it }
    }
}
