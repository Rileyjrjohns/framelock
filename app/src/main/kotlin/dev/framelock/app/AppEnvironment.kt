package dev.framelock.app

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

internal class AppEnvironment(
    context: Context,
) {
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val testVideo = File(context.filesDir, "perf/input.mp4")
}
