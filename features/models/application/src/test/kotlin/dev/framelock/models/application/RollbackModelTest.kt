package dev.framelock.models.application

import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.models.domain.AuditAction
import dev.framelock.models.domain.AuditOutcome
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.telemetry.ModelTelemetry
import dev.framelock.testing.FakeModelRepository
import dev.framelock.testing.FakeModelRepository.Operation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** ADR-003: the previous version stays on disk; rolling back follows the same rules as activating. */
class RollbackModelTest {
    private val h = ModelUseCaseHarness()

    @BeforeEach
    fun activateSecondModel() =
        runTest {
            h.activate(h.m2.manifest.version)
            h.pipeline.loads.clear()
            h.recorded.clear()
        }

    @ParameterizedTest
    @MethodSource("dev.framelock.models.application.ModelUseCaseHarness#sessionStates")
    fun `refused while a session is active`(state: PipelineState) =
        runTest {
            h.pipeline.setState(state)

            val result = h.rollback()

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.SWAP_REFUSED), result)
            assertEquals(0, h.repository.rollbackCalls)
            assertEquals(listOf(ModelTelemetry.ModelRejected(ModelRejectionReason.SWAP_REFUSED)), h.recorded)
            assertEquals(
                AuditOutcome.REJECTED,
                h.audit.entries
                    .last()
                    .outcome,
            )
        }

    @Test
    fun `succeeds when Idle, loading the previous model before the pointer moves back`() =
        runTest {
            val result = h.rollback()

            assertEquals(ModelChangeResult.Changed(h.m2.manifest.version, h.m1.manifest.version), result)
            assertEquals(listOf(h.m1.manifest.version to h.m2.manifest.version), h.pipeline.loads)
            assertEquals(h.m1, h.repository.active())
        }

    @Test
    fun `succeeds when NoSignal`() =
        runTest {
            h.pipeline.setState(ModelUseCaseHarness.NO_SIGNAL)

            val result = h.rollback()

            assertEquals(ModelChangeResult.Changed(h.m2.manifest.version, h.m1.manifest.version), result)
        }

    @Test
    fun `emits ModelSwapped from current to previous and records an audit entry`() =
        runTest {
            h.rollback()

            assertEquals(listOf(ModelTelemetry.ModelSwapped(h.m2.manifest.version, h.m1.manifest.version)), h.recorded)
            val entry = h.audit.entries.last()
            assertEquals(AuditAction.ROLLBACK, entry.action)
            assertEquals(AuditOutcome.SUCCEEDED, entry.outcome)
        }

    @Test
    fun `fails with NO_PREVIOUS_VERSION when none exists`() =
        runTest {
            val fresh = ModelUseCaseHarness()
            val single = FakeModelRepository(initial = listOf(fresh.m1), active = fresh.m1.manifest.version)
            val rollback =
                RollbackModel(FakeModelLoading(single), single, fresh.audit, { fresh.recorded += it }, fresh.clock)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.NO_PREVIOUS_VERSION), rollback())
            assertEquals(listOf(ModelTelemetry.ModelRejected(ModelRejectionReason.NO_PREVIOUS_VERSION)), fresh.recorded)
            assertEquals(
                AuditOutcome.REJECTED,
                fresh.audit.entries
                    .single()
                    .outcome,
            )
        }

    @Test
    fun `rollback load failure keeps the current model`() =
        runTest {
            h.pipeline.failNextLoad()

            val result = h.rollback()

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.LOAD_FAILED), result)
            assertEquals(h.m2, h.repository.active())
        }

    @Test
    fun `repository failure after the load reloads the current model`() =
        runTest {
            h.repository.failNext(Operation.ROLLBACK, ModelRejectedException(ModelRejectionReason.IO_ERROR))

            val result = h.rollback()

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.IO_ERROR), result)
            assertEquals(listOf(h.m1.manifest.version, h.m2.manifest.version), h.pipeline.loads.map { it.first })
        }
}
