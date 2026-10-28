package dev.framelock.app

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
