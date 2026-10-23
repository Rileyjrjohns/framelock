package dev.framelock.live.domain.frame

/** Monotonic within a session; restarts at 0 for each new session. */
@JvmInline
value class FrameId(
    val value: Long,
) : Comparable<FrameId> {
    init {
        require(value >= 0) { "FrameId must be >= 0, was $value" }
    }

    fun isAfter(other: FrameId): Boolean = value > other.value

    override fun compareTo(other: FrameId): Int = value.compareTo(other.value)
}

/** Incremented on every (re)connection; allocated by the source adapter (ADR-010). */
@JvmInline
value class SessionId(
    val value: Long,
) : Comparable<SessionId> {
    init {
        require(value >= 1) { "SessionId must be >= 1, was $value" }
    }

    fun next(): SessionId = SessionId(value + 1)

    override fun compareTo(other: SessionId): Int = value.compareTo(other.value)

    companion object {
        val FIRST = SessionId(1)
    }
}

/**
 * Allocates session ids, strictly increasing from [SessionId.FIRST]. One instance is shared by every source of the
 * app, so a switch from one source to another never gives the pipeline a non-increasing id. Thread-safe: sources
 * allocate from their own threads.
 */
class SessionIdSequence {
    private var last = 0L

    @Synchronized
    fun next(): SessionId = SessionId(++last)
}

/** Allocates frame ids for one source. Not thread-safe: a source emits from one thread. */
class FrameIdSequence {
    private var next = 0L

    fun next(): FrameId = FrameId(next++)

    fun restart() {
        next = 0
    }
}

/**
 * A captured frame. Its [image] belongs to whoever holds the frame: the holder closes it exactly once, which
 * returns the pixels to their pool (ADR-010).
 */
class Frame(
    val id: FrameId,
    val sessionId: SessionId,
    val captureTimestampNanos: Long,
    val width: Int,
    val height: Int,
    val image: ImageBuffer,
) {
    init {
        require(width > 0 && height > 0) { "Frame size must be positive, was ${width}x$height" }
        require(captureTimestampNanos >= 0) { "Capture timestamp must be >= 0, was $captureTimestampNanos" }
    }

    override fun toString(): String = "Frame(session=${sessionId.value}, id=${id.value})"
}
