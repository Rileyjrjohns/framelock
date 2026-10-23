package dev.framelock.testing

import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A [FrameSource] driven by the test. [connect], [emit] and [disconnect] queue events; the collector of [events]
 * receives them in order.
 *
 * Buffer ownership follows ADR-010: a frame that is never delivered is closed by the source. That covers a frame
 * handed to a collector that is cancelled before it resumes, a frame still queued when [close] is called, and a frame
 * emitted while the source is closed. After a collector stops, the frames still queued stay queued for the next
 * collector, until [close] releases them.
 *
 * Thread-safe: a test or an adapter-like thread may emit while the pipeline collects.
 */
class FakeFrameSource : FrameSource {
    private val lock = Any()
    private val channel =
        Channel<SourceEvent>(Channel.UNLIMITED, onUndeliveredElement = ::releaseFrameOf)
    private var closed = false
    private var open = false
    private var openCallCount = 0
    private var closeCallCount = 0
    private var pendingOpenFailures = 0
    private var openFailure: Exception = DEFAULT_OPEN_FAILURE

    override val events: Flow<SourceEvent> = channel.receiveAsFlow()

    /**
     * Called inside [open] after the failure check, with the 1-based index of this call among all `open()` calls
     * (failed ones included). Tests use it to script the events of the new connection, typically with [connect].
     */
    @Volatile
    var onOpen: suspend (attempt: Int) -> Unit = {}

    val openCalls: Int get() = synchronized(lock) { openCallCount }
    val closeCalls: Int get() = synchronized(lock) { closeCallCount }

    /** True between a successful [open] and the next [close]. */
    val isOpen: Boolean get() = synchronized(lock) { open }

    /** Queues the start of a new session. */
    fun connect(
        sessionId: SessionId,
        spec: StreamSpec = TestFrames.SPEC,
    ) {
        channel.trySend(SourceEvent.Connected(spec, sessionId))
    }

    /** Queues [frame]; the collector owns it from then on. While the source is closed, closes it instead. */
    fun emit(frame: Frame) {
        synchronized(lock) {
            if (closed) {
                frame.image.close()
            } else {
                channel.trySend(SourceEvent.FrameAvailable(frame))
            }
        }
    }

    fun disconnect(reason: DisconnectReason) {
        channel.trySend(SourceEvent.Disconnected(reason))
    }

    /** Makes the next [count] calls of [open] throw [reason], then lets them succeed. */
    fun failNextOpens(
        count: Int,
        reason: Exception = DEFAULT_OPEN_FAILURE,
    ) {
        require(count >= 0) { "count must be >= 0, was $count" }
        synchronized(lock) {
            pendingOpenFailures = count
            openFailure = reason
        }
    }

    override suspend fun open() {
        val attempt = synchronized(lock) { ++openCallCount }
        val failure =
            synchronized(lock) {
                if (pendingOpenFailures > 0) {
                    pendingOpenFailures--
                    openFailure
                } else {
                    null
                }
            }
        if (failure != null) throw failure
        synchronized(lock) {
            open = true
            closed = false
        }
        onOpen(attempt)
    }

    /** Releases the frames still queued. Other queued events stay queued. */
    override suspend fun close() {
        synchronized(lock) {
            closeCallCount++
            open = false
            closed = true
            releaseQueuedFrames()
        }
    }

    private fun releaseQueuedFrames() {
        val kept = mutableListOf<SourceEvent>()
        while (true) {
            val event = channel.tryReceive().getOrNull() ?: break
            if (event is SourceEvent.FrameAvailable) event.frame.image.close() else kept += event
        }
        kept.forEach { channel.trySend(it) }
    }

    private companion object {
        val DEFAULT_OPEN_FAILURE = IllegalStateException("open failed")
    }
}

private fun releaseFrameOf(event: SourceEvent) {
    if (event is SourceEvent.FrameAvailable) event.frame.image.close()
}
