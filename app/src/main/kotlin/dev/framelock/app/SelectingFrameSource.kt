package dev.framelock.app

import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * The source the pipeline sees: it forwards to one of four sources, chosen when a session opens (ADR-016). Only `:app`
 * knows every adapter, so the choice lives here.
 *
 * The user's [choice] decides:
 * - an explicit choice opens that source: [external], [uvc] (the device the UVC source was told to use), [back],
 *   [front] or, in debug builds, [file] (the looping test video of the M7 measurements, never chosen by Auto);
 * - [SourceChoice.Auto] applies the order of preference:
 *   1. [external]: a camera Camera2 exposes as external, which is how a capture card appears on a device with
 *      `android.hardware.camera.external`;
 *   2. [uvc]: an attached USB video class device, driven by the UVC library (the first one);
 *   3. [back]: the back camera.
 *
 * [open] chooses when no source is chosen yet. The choice then stays until [close]: the pipeline's reconnections call
 * [open] again without closing, and must reach the same source even if the device list changed meanwhile (a detached
 * capture card keeps being retried; it does not silently turn the app to the back camera). So a new [choice] applies at
 * the next session, after [close]; the UI only offers it while the pipeline is idle.
 *
 * [events] merges the events of the sources; only the chosen one emits. Session ids stay increasing across them
 * because the sources share one `SessionIdSequence`.
 *
 * [activeDescription] is what the HUD shows: the label of the chosen camera, or, for [uvc], [uvcDescription] (device
 * and negotiated mode, set by the UVC source for the life of a stream). Null when no source is chosen. Not telemetry.
 * It is shared in [scope], so [scope] must outlive this source.
 */
class SelectingFrameSource(
    private val external: FrameSource,
    private val uvc: FrameSource,
    private val back: FrameSource,
    private val front: FrameSource,
    private val file: FrameSource? = null,
    private val choice: () -> SourceChoice,
    private val hasExternalCamera: suspend () -> Boolean,
    private val hasUvcDevice: () -> Boolean,
    labels: StateFlow<SourceLabels>,
    uvcDescription: StateFlow<String?>,
    scope: CoroutineScope,
) : FrameSource {
    override val events: Flow<SourceEvent> =
        listOfNotNull(external.events, uvc.events, back.events, front.events, file?.events).merge()

    private val chosen = MutableStateFlow<FrameSource?>(null)

    val activeDescription: StateFlow<String?> =
        combine(chosen, uvcDescription, labels) { source, uvcMode, names ->
            when {
                source == null -> null
                source === uvc -> uvcMode
                source === external -> names.external
                source === front -> names.front
                source === file -> names.file
                else -> names.back
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Picks the source for the next session and remembers it until [close]. */
    suspend fun choose(): FrameSource =
        chosen.value ?: when (choice()) {
            SourceChoice.Auto -> {
                when {
                    hasExternalCamera() -> external
                    hasUvcDevice() -> uvc
                    else -> back
                }
            }

            SourceChoice.External -> {
                external
            }

            SourceChoice.Back -> {
                back
            }

            SourceChoice.Front -> {
                front
            }

            SourceChoice.File -> {
                checkNotNull(file) { "This build has no test video source" }
            }

            is SourceChoice.Uvc -> {
                uvc
            }
        }.also { chosen.value = it }

    override suspend fun open() = choose().open()

    override suspend fun close() {
        val source = chosen.value ?: return
        chosen.value = null
        source.close()
    }
}
