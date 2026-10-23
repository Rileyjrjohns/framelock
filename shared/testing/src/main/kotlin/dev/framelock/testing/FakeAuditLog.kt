package dev.framelock.testing

import dev.framelock.models.domain.AuditEntry
import dev.framelock.models.domain.port.AuditLog
import java.util.concurrent.CopyOnWriteArrayList

/** An [AuditLog] that keeps what it receives. Thread-safe. */
class FakeAuditLog : AuditLog {
    private val recorded = CopyOnWriteArrayList<AuditEntry>()

    /** Entries recorded so far, in order. */
    val entries: List<AuditEntry> get() = recorded.toList()

    override fun record(entry: AuditEntry) {
        recorded += entry
    }
}
