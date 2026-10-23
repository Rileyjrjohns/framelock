package dev.framelock.live.application.pipeline

import dev.framelock.kernel.model.ModelArtifact
import kotlinx.coroutines.flow.StateFlow

/**
 * What the models feature needs from the pipeline (ADR-003, ADR-014): its state, to apply `ModelSwapPolicy`, and
 * loading a model between sessions. Implemented by [InferencePipeline].
 */
interface ModelLoading {
    val state: StateFlow<PipelineUiState>

    /**
     * Loads and warms up [model] on the engine's thread. Throws [SessionActiveException] if a session is running, or
     * the engine's error if loading fails; the previous model then stays loaded. A session cannot start meanwhile.
     */
    suspend fun loadModel(model: ModelArtifact)
}

class SessionActiveException : IllegalStateException("A model can only be loaded between sessions")
