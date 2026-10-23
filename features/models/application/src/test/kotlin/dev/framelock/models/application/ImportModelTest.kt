package dev.framelock.models.application

import dev.framelock.live.domain.pipeline.InferenceRate
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.models.domain.AuditAction
import dev.framelock.models.domain.AuditOutcome
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import dev.framelock.models.domain.telemetry.ModelTelemetry
import dev.framelock.testing.FakeModelRepository.Operation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream

/** ADR-003: imports are verified by the repository; every outcome is audited, every rejection reported. */
class ImportModelTest {
    private val h = ModelUseCaseHarness()
    private val source =
        object : ModelSource {
            override val displayName = "detector-v3"

            override fun open(): InputStream = InputStream.nullInputStream()
        }

    @Test
    fun `successful import returns the manifest and records an audit entry`() =
        runTest {
            val result = h.import(source)

            assertTrue(result is ImportResult.Imported, "$result")
            val entry = h.audit.entries.single()
            assertEquals(AuditAction.IMPORT, entry.action)
            assertEquals(AuditOutcome.SUCCEEDED, entry.outcome)
            assertEquals((result as ImportResult.Imported).manifest.version, entry.version)
        }

    @Test
    fun `rejected import emits ModelRejected with its reason and records an audit entry`() =
        runTest {
            h.repository.failNext(Operation.IMPORT, ModelRejectedException(ModelRejectionReason.BAD_SIGNATURE))

            val result = h.import(source)

            assertEquals(ImportResult.Refused(ModelRejectionReason.BAD_SIGNATURE), result)
            assertEquals(listOf(ModelTelemetry.ModelRejected(ModelRejectionReason.BAD_SIGNATURE)), h.recorded)
            assertEquals(
                AuditOutcome.REJECTED,
                h.audit.entries
                    .single()
                    .outcome,
            )
        }

    @Test
    fun `unexpected import failure is reported as an IO error`() =
        runTest {
            h.repository.failNext(Operation.IMPORT, java.io.IOException("disk full"))

            val result = h.import(source)

            assertEquals(ImportResult.Refused(ModelRejectionReason.IO_ERROR), result)
            assertEquals(
                AuditOutcome.FAILED,
                h.audit.entries
                    .single()
                    .outcome,
            )
        }

    @Test
    fun `import is refused while a session is running`() =
        runTest {
            h.pipeline.setState(PipelineState.Running(InferenceRate.UNLIMITED))

            val result = h.import(source)

            assertEquals(ImportResult.Refused(ModelRejectionReason.SWAP_REFUSED), result)
            assertEquals(0, h.repository.importCalls)
        }
}
