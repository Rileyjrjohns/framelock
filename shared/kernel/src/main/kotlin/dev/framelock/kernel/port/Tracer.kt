package dev.framelock.kernel.port

enum class TraceSection { CAPTURE, PREPROCESS, INFERENCE, POSTPROCESS, RENDER }

/** ADR-009: begin and end on the same thread; use [trace] rather than calling them directly. */
interface Tracer {
    fun begin(section: TraceSection)

    fun end()
}

inline fun <T> Tracer.trace(
    section: TraceSection,
    block: () -> T,
): T {
    begin(section)
    try {
        return block()
    } finally {
        end()
    }
}
