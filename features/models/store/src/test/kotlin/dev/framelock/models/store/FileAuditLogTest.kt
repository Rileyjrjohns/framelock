package dev.framelock.models.store

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.domain.AuditAction
import dev.framelock.models.domain.AuditEntry
import dev.framelock.models.domain.AuditOutcome
import dev.framelock.models.domain.ModelRejectionReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileAuditLogTest {
    @TempDir
    lateinit var filesDir: File

    private var now = 1_700_000_000_000L
    private val log get() = File(filesDir, "audit/audit.log")
    private val old get() = File(filesDir, "audit/audit.log.1")

    private fun entry(
        action: AuditAction = AuditAction.IMPORT,
        version: String? = "v1",
        outcome: AuditOutcome = AuditOutcome.SUCCEEDED,
        reason: ModelRejectionReason? = null,
    ) = AuditEntry(action, version?.let(::ModelVersion), outcome, reason, atNanos = 42)

    @Test
    fun `an entry is stamped with the wall clock at write time, not the monotonic one`() {
        val audit = FileAuditLog(filesDir, wallClockMillis = { now })

        audit.record(entry())
        now += 5_000
        audit.record(entry(AuditAction.ACTIVATE, "v2"))

        assertEquals(
            listOf(
                "1700000000000 IMPORT v1 SUCCEEDED -",
                "1700000005000 ACTIVATE v2 SUCCEEDED -",
            ),
            log.readLines(),
        )
    }

    @Test
    fun `a rejection keeps its reason and a missing version is a dash`() {
        FileAuditLog(filesDir, wallClockMillis = { now })
            .record(entry(AuditAction.IMPORT, null, AuditOutcome.REJECTED, ModelRejectionReason.BAD_SIGNATURE))

        assertEquals(listOf("1700000000000 IMPORT - REJECTED BAD_SIGNATURE"), log.readLines())
    }

    @Test
    fun `entries survive a new instance`() {
        FileAuditLog(filesDir, wallClockMillis = { now }).record(entry())
        FileAuditLog(filesDir, wallClockMillis = { now }).record(entry(AuditAction.ROLLBACK))

        assertEquals(2, log.readLines().size)
    }

    @Test
    fun `the file rotates at the cap and keeps one old file, so the log stays bounded`() {
        val audit = FileAuditLog(filesDir, maxBytes = 200, wallClockMillis = { now++ })

        repeat(200) { audit.record(entry()) }

        assertTrue(old.exists())
        assertTrue(log.length() < 200 + 60)
        assertTrue(old.length() < 200 + 60)
        assertEquals(setOf("audit.log", "audit.log.1"), File(filesDir, "audit").list()!!.toSet())
        // The newest entry is in the live file.
        assertTrue(log.readLines().last().startsWith("${now - 1} "))
    }

    @Test
    fun `no rotation below the cap`() {
        val audit = FileAuditLog(filesDir, wallClockMillis = { now })

        repeat(10) { audit.record(entry()) }

        assertFalse(old.exists())
    }

    @Test
    fun `a log that cannot be written is dropped without throwing`() {
        File(filesDir, "audit").writeText("a file where the directory should be")

        FileAuditLog(filesDir).record(entry())
    }
}
