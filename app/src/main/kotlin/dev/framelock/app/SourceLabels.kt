package dev.framelock.app

import dev.framelock.live.ui.SourceOption

/** Localized built-in source names; device-reported names stay unchanged. */
data class SourceLabels(
    val auto: String,
    val back: String,
    val front: String,
    val external: String,
    val file: String,
) {
    fun relabel(option: SourceOption): SourceOption =
        option.copy(
            label =
                when (choiceOf(option.id)) {
                    SourceChoice.Auto -> auto
                    SourceChoice.Back -> back
                    SourceChoice.Front -> front
                    SourceChoice.External -> external
                    SourceChoice.File -> file
                    is SourceChoice.Uvc -> option.label
                },
        )
}
