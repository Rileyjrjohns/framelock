package dev.framelock.live.application.pipeline

import dev.framelock.kernel.model.ModelArtifact
import kotlinx.coroutines.flow.StateFlow

/**
 * What the models feature needs from the pipeline (ADR-003, ADR-014): its state, to apply `ModelSwapPolicy`, and an
 * atomic model swap. Implemented by [InferencePipeline].
 */
interface ModelLoading {
    val state: StateFlow<PipelineUiState>

    /**
     * Swaps the model under the pipeline's lifecycle lock, so no session starts in the middle (ADR-003):
     * 1. refuses if a session is connected;
     * 2. loads and warms up [target] on the engine's thread;
     * 3. runs [commit] (the repository moves its pointer);
     * 4. if [commit] fails, loads [restore] back, so engine and repository agree.
     * Steps 3 and 4 are not cancellable: a cancelled caller cannot leave the two out of sync.
     */
    suspend fun swapModel(
        target: ModelArtifact,
        restore: ModelArtifact?,
        commit: suspend () -> Result<Unit>,
    ): SwapOutcome
}

sealed interface SwapOutcome {
    data object Swapped : SwapOutcome

    /** A session is connected: models change only between sessions or without signal. */
    data object SessionActive : SwapOutcome

    /** The engine could not load the target; the previous model is still loaded. */
    data class LoadFailed(
        val cause: Throwable,
    ) : SwapOutcome

    /** The commit failed; [restored] tells whether the previous model could be loaded back. */
    data class CommitFailed(
        val cause: Throwable,
        val restored: Boolean,
    ) : SwapOutcome
}

class SessionActiveException : IllegalStateException("A model can only be loaded between sessions")
