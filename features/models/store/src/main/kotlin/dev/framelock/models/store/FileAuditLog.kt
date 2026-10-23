package dev.framelock.models.store

import dev.framelock.models.domain.AuditEntry
import dev.framelock.models.domain.port.AuditLog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The audit trail (ADR-003, amendment M5): `filesDir/audit/audit.log`, one line per entry,
 * `<epoch millis> <ACTION> <version or -> <OUTCOME> <REASON or ->`. Only enums, a version token and a number: no free
 * text (ADR-004). The time is the wall clock read here, when the line is written: the pipeline's [dev.framelock.kernel.port.Clock]
 * is monotonic and restarts at boot, so [AuditEntry.atNanos] is not recorded.
 *
 * At [maxBytes] the file becomes `audit.log.1`, replacing the previous one: at most about twice [maxBytes] stay on disk.
 * A write that fails is dropped: an audit line must never undo a swap that has already happened.
 */
class FileAuditLog(
    filesDir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) : AuditLog {
    private val file = File(File(filesDir, "audit"), "audit.log")
    private val old = File(file.parentFile, "audit.log.1")

    @Synchronized
    override fun record(entry: AuditEntry) {
        val line =
            listOf(
                wallClockMillis().toString(),
                entry.action.name,
                entry.version?.value ?: NONE,
                entry.outcome.name,
                entry.reason?.name ?: NONE,
            ).joinToString(" ") + "\n"
        try {
            file.parentFile?.mkdirs()
            if (file.length() >= maxBytes) {
                Files.move(file.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            file.appendText(line, Charsets.US_ASCII)
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            // See the class comment.
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024
        private const val NONE = "-"
    }
}
