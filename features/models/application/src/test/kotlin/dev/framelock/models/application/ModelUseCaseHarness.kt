package dev.framelock.models.application

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.live.application.pipeline.ModelLoading
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.pipeline.SwapOutcome
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.domain.pipeline.InferenceRate
import dev.framelock.live.domain.pipeline.PauseCause
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.testing.FakeAuditLog
import dev.framelock.testing.FakeModelRepository
import dev.framelock.testing.ManualClock
import dev.framelock.testing.TestModels
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The pipeline as the models feature sees it: a state, and an atomic swap following the [ModelLoading] contract,
 * whose loads can be scripted to fail. `RealPipelineSwapTest` checks the same scenarios on the real pipeline.
 */
internal class FakeModelLoading(
    private val repository: FakeModelRepository,
) : ModelLoading {
    override val state = MutableStateFlow(PipelineUiState.INITIAL)

    /** Each load, with the repository's active version at that moment: proves loads happen before the switch. */
    val loads = mutableListOf<Pair<ModelVersion, ModelVersion?>>()
    var sessionStartedMeanwhile = false
    private val failures = ArrayDeque<Exception>()

    /** When a commit fails, loading the previous model back fails too. */
    var failRestore = false

    fun failNextLoad() = failures.addLast(IllegalStateException("load failed"))

    fun setState(next: PipelineState) {
        state.value = state.value.copy(state = next)
    }

    override suspend fun swapModel(
        target: ModelArtifact,
        restore: ModelArtifact?,
        commit: suspend () -> Result<Unit>,
    ): SwapOutcome {
        val loadFailure = failures.removeFirstOrNull()
        return when {
            sessionStartedMeanwhile -> SwapOutcome.SessionActive
            loadFailure != null -> SwapOutcome.LoadFailed(loadFailure)
            else -> commitAfterLoading(target, restore, commit)
        }
    }

    private suspend fun commitAfterLoading(
        target: ModelArtifact,
        restore: ModelArtifact?,
        commit: suspend () -> Result<Unit>,
    ): SwapOutcome {
        load(target)
        val failure = commit().exceptionOrNull()
        val restored = failure != null && restore != null && !failRestore && load(restore) == Unit
        return if (failure == null) SwapOutcome.Swapped else SwapOutcome.CommitFailed(failure, restored)
    }

    private fun load(model: ModelArtifact) {
        loads += model.manifest.version to repository.active()?.manifest?.version
        state.value = state.value.copy(model = model.manifest.version)
    }
}

internal class ModelUseCaseHarness {
    val m1 = TestModels.artifact("m1")
    val m2 = TestModels.artifact("m2")
    val repository = FakeModelRepository(initial = listOf(m1, m2), active = m1.manifest.version)
    val pipeline = FakeModelLoading(repository)
    val audit = FakeAuditLog()
    val recorded = mutableListOf<TelemetryEvent>()
    private val record: (TelemetryEvent) -> Unit = { recorded += it }
    val clock = ManualClock(startNanos = 1_000)

    val activate = ActivateModel(pipeline, repository, audit, record, clock)
    val rollback = RollbackModel(pipeline, repository, audit, record, clock)
    val import = ImportModel(pipeline, repository, audit, record, clock)

    companion object {
        /** Every state in which ModelSwapPolicy refuses a change. */
        @JvmStatic
        fun sessionStates(): List<PipelineState> =
            listOf(
                PipelineState.Connecting,
                PipelineState.Running(InferenceRate.UNLIMITED),
                PipelineState.Degraded(DegradationCause.THERMAL, InferenceRate.capped(10)),
                PipelineState.Paused(PauseCause.MEMORY),
            )

        val NO_SIGNAL = PipelineState.NoSignal(DisconnectReason.DEVICE_DETACHED)
    }
}
