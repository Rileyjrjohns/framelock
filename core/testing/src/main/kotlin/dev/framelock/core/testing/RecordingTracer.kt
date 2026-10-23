package dev.framelock.core.testing

import dev.framelock.core.domain.port.TraceSection
import dev.framelock.core.domain.port.Tracer

/**
 * A [Tracer] that keeps a log of every `begin` and `end` and checks that sections are balanced per thread (ADR-009:
 * a section ends on the thread that began it). It never throws on misuse, so that the code under test keeps running;
 * [assertBalanced] reports it. Thread-safe.
 */
class RecordingTracer : Tracer {
    private class ThreadSections(
        val threadName: String,
    ) {
        val open = mutableListOf<TraceSection>()
        var endsWithoutBegin = 0
    }

    private val lock = Any()
    private val log = mutableListOf<String>()
    private val threads = mutableMapOf<Thread, ThreadSections>()

    /** `begin INFERENCE @<thread>` and `end INFERENCE @<thread>`, in call order across all threads. */
    val records: List<String> get() = synchronized(lock) { log.toList() }

    override fun begin(section: TraceSection) {
        synchronized(lock) {
            val sections = sectionsOfCurrentThread()
            sections.open += section
            log += "begin $section @${sections.threadName}"
        }
    }

    override fun end() {
        synchronized(lock) {
            val sections = sectionsOfCurrentThread()
            val section = sections.open.removeLastOrNull()
            if (section == null) sections.endsWithoutBegin++
            log += "end ${section ?: UNBALANCED} @${sections.threadName}"
        }
    }

    /** The innermost section begun and not yet ended by the calling thread. */
    fun currentSection(): TraceSection? = synchronized(lock) { threads[Thread.currentThread()]?.open?.lastOrNull() }

    /** @throws AssertionError if a thread has open sections, or ended a section it did not begin. */
    fun assertBalanced() {
        val problems =
            synchronized(lock) {
                threads.values.flatMap { sections ->
                    buildList {
                        if (sections.open.isNotEmpty()) {
                            add("sections still open on ${sections.threadName}: ${sections.open}")
                        }
                        if (sections.endsWithoutBegin > 0) {
                            add("end without begin on ${sections.threadName}: ${sections.endsWithoutBegin} time(s)")
                        }
                    }
                }
            }
        if (problems.isNotEmpty()) {
            throw AssertionError("Unbalanced trace sections: " + problems.joinToString("; "))
        }
    }

    private fun sectionsOfCurrentThread(): ThreadSections =
        threads.getOrPut(Thread.currentThread()) { ThreadSections(currentThreadName()) }

    private companion object {
        const val UNBALANCED = "<none>"
    }
}
