package dev.framelock.app

/** Which source the user asked for. [Auto] is today's order of preference (see [SelectingFrameSource]). */
sealed interface SourceChoice {
    data object Auto : SourceChoice

    data object External : SourceChoice

    data object Back : SourceChoice

    data object Front : SourceChoice

    /** The looping test video (debug builds, M7 measurements). */
    data object File : SourceChoice

    /** The attached UVC device with [deviceId] (device ID). */
    data class Uvc(
        val deviceId: String,
    ) : SourceChoice
}
