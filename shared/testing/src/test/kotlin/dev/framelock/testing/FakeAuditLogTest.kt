package dev.framelock.testing

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.domain.AuditAction
import dev.framelock.models.domain.AuditEntry
import dev.framelock.models.domain.AuditOutcome
import dev.framelock.models.domain.ModelRejectionReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeAuditLogTest {
    @Test
    fun `records entries in order`() {
        val log = FakeAuditLog()
        assertTrue(log.entries.isEmpty())
        val imported = AuditEntry(AuditAction.IMPORT, ModelVersion("v2"), AuditOutcome.SUCCEEDED, null, 10)
        val rejected =
            AuditEntry(
                AuditAction.ACTIVATE,
                ModelVersion("v2"),
                AuditOutcome.REJECTED,
                ModelRejectionReason.SWAP_REFUSED,
                20,
            )
        val rolledBack =
            AuditEntry(AuditAction.ROLLBACK, null, AuditOutcome.FAILED, ModelRejectionReason.NO_PREVIOUS_VERSION, 30)

        log.record(imported)
        log.record(rejected)
        log.record(rolledBack)

        assertEquals(listOf(imported, rejected, rolledBack), log.entries)
    }
}
