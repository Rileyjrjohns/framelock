package dev.framelock.telemetry.room

/** A [TelemetryBuffer] in a list, for tests of the sink and the upload run. Single-threaded use. */
class InMemoryTelemetryBuffer : TelemetryBuffer {
    private var nextId = 1L
    private val rows = mutableListOf<Pair<Long, String>>()

    /** When set, [append] throws it. */
    var appendFailure: RuntimeException? = null

    /** Rows held, oldest first, as (id, payload). */
    val stored: List<Pair<Long, String>> get() = rows.toList()

    val payloads: List<String> get() = rows.map { it.second }

    fun seed(count: Int) {
        repeat(count) { rows += nextId++ to """{"type":"AppSession","atMillis":$it}""" }
    }

    override suspend fun append(payloads: List<Pair<Long, String>>) {
        appendFailure?.let { throw it }
        payloads.forEach { rows += nextId++ to it.second }
    }

    override suspend fun oldest(limit: Int): List<StoredEvent> =
        rows.take(limit).map { StoredEvent(it.first, it.second) }

    override suspend fun delete(ids: List<Long>) {
        rows.removeAll { it.first in ids }
    }

    override suspend fun count(): Int = rows.size
}
