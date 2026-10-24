package dev.framelock.app

import dev.framelock.live.capture.uvc.UvcDeviceInfo
import dev.framelock.live.ui.SourceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which source the user asked for. [Auto] is today's order of preference (see [SelectingFrameSource]). */
sealed interface SourceChoice {
    data object Auto : SourceChoice

    data object External : SourceChoice

    data object Back : SourceChoice

    data object Front : SourceChoice

    /** The looping test video (debug builds, M7 measurements). */
    data object File : SourceChoice

    /** The attached UVC device with [deviceId] (`UvcDeviceInfo.id`). */
    data class Uvc(
        val deviceId: String,
    ) : SourceChoice
}

/** Fixed texts of the picker and the HUD. English, like the rest of the UI; they come from resources in `:app`. */
data class SourceLabels(
    val auto: String,
    val back: String,
    val front: String,
    val external: String,
    val file: String = "Test video",
)

private const val UVC_PREFIX = "uvc:"
private const val AUTO_ID = "auto"
private const val BACK_ID = "back"
private const val FRONT_ID = "front"
private const val EXTERNAL_ID = "external"
private const val FILE_ID = "file"

/**
 * The picker entries for what is available now: `Auto` always, then the cameras CameraX reports, then one entry per
 * attached UVC device, then the test video when [hasTestVideo] (debug builds with the file in place). The back camera is
 * listed when CameraX reports it. Pure, so it is tested on the JVM.
 */
fun buildSourceOptions(
    labels: SourceLabels,
    hasBack: Boolean,
    hasFront: Boolean,
    hasExternal: Boolean,
    uvcDevices: List<UvcDeviceInfo>,
    hasTestVideo: Boolean = false,
): List<SourceOption> =
    buildList {
        add(SourceOption(AUTO_ID, labels.auto))
        if (hasBack) add(SourceOption(BACK_ID, labels.back))
        if (hasFront) add(SourceOption(FRONT_ID, labels.front))
        if (hasExternal) add(SourceOption(EXTERNAL_ID, labels.external))
        uvcDevices.forEach { add(SourceOption(UVC_PREFIX + it.id, it.label)) }
        if (hasTestVideo) add(SourceOption(FILE_ID, labels.file))
    }

/** The choice an option id stands for; an unknown id is [SourceChoice.Auto]. */
fun choiceOf(optionId: String): SourceChoice =
    when {
        optionId == BACK_ID -> SourceChoice.Back
        optionId == FRONT_ID -> SourceChoice.Front
        optionId == EXTERNAL_ID -> SourceChoice.External
        optionId == FILE_ID -> SourceChoice.File
        optionId.startsWith(UVC_PREFIX) -> SourceChoice.Uvc(optionId.removePrefix(UVC_PREFIX))
        else -> SourceChoice.Auto
    }

/**
 * The user's source choice, shared by the picker (through the view model) and [SelectingFrameSource]. [listSources]
 * is asked each time the picker opens; the options it returns are remembered so that [select] can find the label of
 * an id. The choice survives the device being unplugged: it is then retried like any lost source.
 *
 * Thread-safe: [current] and [choice] are read from the pipeline's threads while the UI selects.
 */
class SourceSelection(
    private val labels: SourceLabels,
    private val listSources: suspend () -> List<SourceOption>,
) {
    private val auto = SourceOption(AUTO_ID, labels.auto)
    private val selected = MutableStateFlow(auto)

    @Volatile private var known = listOf(auto)

    val current: StateFlow<SourceOption> = selected.asStateFlow()

    /** What the pipeline should open next. */
    val choice: SourceChoice get() = choiceOf(selected.value.id)

    suspend fun options(): List<SourceOption> = listSources().also { known = it }

    /** Selects the option with [id], among those last listed; an id that is not there is ignored. */
    fun select(id: String) {
        known.firstOrNull { it.id == id }?.let { selected.value = it }
    }
}
