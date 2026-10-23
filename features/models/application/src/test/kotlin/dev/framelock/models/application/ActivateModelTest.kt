package dev.framelock.models.application

import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.models.domain.AuditAction
import dev.framelock.models.domain.AuditOutcome
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.telemetry.ModelTelemetry
import dev.framelock.testing.FakeModelRepository.Operation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** ADR-003: activation only between sessions, load before switching, previous model kept on failure. */
class ActivateModelTest {
    private val h = ModelUseCaseHarness()

    @ParameterizedTest
    @MethodSource("dev.framelock.models.application.ModelUseCaseHarness#sessionStates")
    fun `refused while a session is active`(state: PipelineState) =
        runTest {
            h.pipeline.setState(state)

            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.SWAP_REFUSED), result)
            assertEquals(emptyList<Any>(), h.pipeline.loads)
            assertEquals(emptyList<Any>(), h.repository.activateCalls)
            assertEquals(listOf(ModelTelemetry.ModelRejected(ModelRejectionReason.SWAP_REFUSED)), h.recorded)
            assertEquals(
                AuditOutcome.REJECTED,
                h.audit.entries
                    .single()
                    .outcome,
            )
        }

    @Test
    fun `succeeds when Idle, loading before the pointer switches`() =
        runTest {
            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Changed(h.m1.manifest.version, h.m2.manifest.version), result)
            assertEquals(listOf(h.m2.manifest.version to h.m1.manifest.version), h.pipeline.loads)
            assertEquals(h.m2, h.repository.active())
        }

    @Test
    fun `succeeds when NoSignal`() =
        runTest {
            h.pipeline.setState(ModelUseCaseHarness.NO_SIGNAL)

            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Changed(h.m1.manifest.version, h.m2.manifest.version), result)
        }

    @Test
    fun `success emits ModelSwapped and records an audit entry`() =
        runTest {
            h.activate(h.m2.manifest.version)

            assertEquals(listOf(ModelTelemetry.ModelSwapped(h.m1.manifest.version, h.m2.manifest.version)), h.recorded)
            val entry = h.audit.entries.single()
            assertEquals(AuditAction.ACTIVATE, entry.action)
            assertEquals(AuditOutcome.SUCCEEDED, entry.outcome)
            assertEquals(h.m2.manifest.version, entry.version)
            assertEquals(h.clock.nowNanos(), entry.atNanos)
        }

    @Test
    fun `activating an unknown version fails and nothing changes`() =
        runTest {
            val result =
                h.activate(
                    dev.framelock.kernel.model
                        .ModelVersion("missing"),
                )

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.UNKNOWN_VERSION), result)
            assertEquals(emptyList<Any>(), h.pipeline.loads)
            assertEquals(h.m1, h.repository.active())
            assertEquals(listOf(ModelTelemetry.ModelRejected(ModelRejectionReason.UNKNOWN_VERSION)), h.recorded)
            assertEquals(
                AuditOutcome.REJECTED,
                h.audit.entries
                    .single()
                    .outcome,
            )
        }

    @Test
    fun `engine load failure keeps the previous model active`() =
        runTest {
            h.pipeline.failNextLoad()

            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.LOAD_FAILED), result)
            assertEquals(h.m1, h.repository.active())
            assertEquals(
                AuditOutcome.FAILED,
                h.audit.entries
                    .single()
                    .outcome,
            )
            assertEquals(listOf(ModelTelemetry.ModelRejected(ModelRejectionReason.LOAD_FAILED)), h.recorded)
        }

    @Test
    fun `repository failure after the load restores the previous model in the engine`() =
        runTest {
            h.repository.failNext(Operation.ACTIVATE, ModelRejectedException(ModelRejectionReason.IO_ERROR))

            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.IO_ERROR), result)
            assertEquals(listOf(h.m2.manifest.version, h.m1.manifest.version), h.pipeline.loads.map { it.first })
            assertEquals(h.m1.manifest.version, h.pipeline.state.value.model)
        }

    @Test
    fun `activating the active model changes nothing`() =
        runTest {
            val result = h.activate(h.m1.manifest.version)

            assertEquals(ModelChangeResult.Unchanged(h.m1.manifest.version), result)
            assertEquals(emptyList<Any>(), h.pipeline.loads)
            assertEquals(emptyList<Any>(), h.recorded)
            assertEquals(emptyList<Any>(), h.audit.entries)
        }

    @Test
    fun `a failed restore after a failed commit is reported too`() =
        runTest {
            h.repository.failNext(Operation.ACTIVATE, java.io.IOException("disk full"))
            h.pipeline.failRestore = true

            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.IO_ERROR), result)
            assertEquals(
                listOf(
                    ModelTelemetry.ModelRejected(ModelRejectionReason.IO_ERROR),
                    ModelTelemetry.ModelRejected(ModelRejectionReason.LOAD_FAILED),
                ),
                h.recorded,
            )
            assertEquals(listOf(AuditOutcome.FAILED, AuditOutcome.FAILED), h.audit.entries.map { it.outcome })
        }

    @Test
    fun `a session started meanwhile turns the activation into a refusal`() =
        runTest {
            h.pipeline.sessionStartedMeanwhile = true

            val result = h.activate(h.m2.manifest.version)

            assertEquals(ModelChangeResult.Refused(ModelRejectionReason.SWAP_REFUSED), result)
            assertEquals(h.m1, h.repository.active())
        }
}
