package dev.framelock.app

import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * The source the pipeline sees: it forwards to one of three sources, chosen when a session opens (ADR-016). Only
 * `:app` knows every adapter, so the choice lives here.
 *
 * Order of preference:
 * 1. [external]: a camera Camera2 exposes as external, which is how a capture card appears on a device with
 *    `android.hardware.camera.external`;
 * 2. [uvc]: an attached USB video class device, driven by the UVC library;
 * 3. [back]: the back camera.
 *
 * [open] chooses when no source is chosen yet. The choice then stays until [close]: the pipeline's reconnections call
 * [open] again without closing, and must reach the same source even if the device list changed meanwhile (a detached
 * capture card keeps being retried; it does not silently turn the app to the back camera).
 *
 * [events] merges the events of the three sources; only the chosen one emits. Session ids stay increasing across them
 * because the sources share one `SessionIdSequence`.
 */
class SelectingFrameSource(
    private val external: FrameSource,
    private val uvc: FrameSource,
    private val back: FrameSource,
    private val hasExternalCamera: suspend () -> Boolean,
    private val hasUvcDevice: () -> Boolean,
) : FrameSource {
    override val events: Flow<SourceEvent> = merge(external.events, uvc.events, back.events)

    @Volatile private var chosen: FrameSource? = null

    /** Picks the source for the next session and remembers it until [close]. */
    suspend fun choose(): FrameSource =
        chosen ?: when {
            hasExternalCamera() -> external
            hasUvcDevice() -> uvc
            else -> back
        }.also { chosen = it }

    override suspend fun open() = choose().open()

    override suspend fun close() {
        val source = chosen ?: return
        chosen = null
        source.close()
    }
}
