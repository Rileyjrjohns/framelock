package dev.framelock.app

import dev.framelock.live.capture.uvc.UvcDeviceInfo
import dev.framelock.live.ui.SourceOption

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
