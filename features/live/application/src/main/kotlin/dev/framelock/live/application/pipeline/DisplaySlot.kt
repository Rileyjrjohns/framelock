package dev.framelock.live.application.pipeline

import dev.framelock.live.domain.frame.Frame

/**
 * Holds the frame on screen (ADR-010). Replacing or clearing it closes the previous buffer under the same lock the
 * renderer reads under, so a buffer is never closed while being read, nor read after being closed.
 */
class DisplaySlot {
    @PublishedApi
    internal val lock = Any()

    @PublishedApi
    internal var frame: Frame? = null

    internal fun show(next: Frame) =
        synchronized(lock) {
            frame?.image?.close()
            frame = next
        }

    internal fun clear() =
        synchronized(lock) {
            frame?.image?.close()
            frame = null
        }

    /** Runs [block] with the frame on screen, or null. Copy the pixels inside; never keep the frame. */
    inline fun <T> read(block: (Frame?) -> T): T = synchronized(lock) { block(frame) }
}
