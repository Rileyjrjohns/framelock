package dev.framelock.app

import dev.framelock.live.inference.litert.BackendPreference
import java.util.Locale

/**
 * What a script asks for through the launch intent of a debug build, so that a measurement run needs no taps:
 *
 * - `--es source <auto|back|front|external|file|uvc:<id>|<id>>`: the video source ([file] is the test video of
 *   `scripts/perf/`); a bare id is a UVC device id;
 * - `--ez autostart true`: start the pipeline once a model is active;
 * - `--ez hud true`: show the debug HUD from the start;
 * - `--es backend <auto|gpu|cpu>`: the inference backend; `auto` (the default) is the production behaviour.
 *
 * [sourceId] is already a source option id (see `buildSourceOptions`). An unknown backend is ignored, so a typo falls
 * back to `auto` rather than to a forced backend. Release builds never read these extras (`MainActivity`).
 */
data class DebugLaunch(
    val sourceId: String? = null,
    val autostart: Boolean = false,
    val hud: Boolean = false,
    val backend: BackendPreference? = null,
) {
    /** Whether any extra asked for something. */
    val isActive: Boolean get() = this != NONE

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_AUTOSTART = "autostart"
        const val EXTRA_HUD = "hud"
        const val EXTRA_BACKEND = "backend"

        val NONE = DebugLaunch()

        private const val UVC_PREFIX = "uvc:"
        private val SOURCE_IDS = setOf("auto", "back", "front", "external", "file")

        /** Reads the extras through [string] and [flag], which the activity binds to its intent. Pure. */
        fun parse(
            string: (String) -> String?,
            flag: (String) -> Boolean,
        ): DebugLaunch =
            DebugLaunch(
                sourceId = string(EXTRA_SOURCE)?.let(::sourceOptionId),
                autostart = flag(EXTRA_AUTOSTART),
                hud = flag(EXTRA_HUD),
                backend = string(EXTRA_BACKEND)?.let(::backendOf),
            )

        private fun sourceOptionId(value: String): String? {
            val trimmed = value.trim()
            val keyword = trimmed.lowercase(Locale.ROOT)
            return when {
                trimmed.isEmpty() -> null
                keyword in SOURCE_IDS -> keyword
                trimmed.startsWith(UVC_PREFIX) -> trimmed
                else -> UVC_PREFIX + trimmed
            }
        }

        private fun backendOf(value: String): BackendPreference? =
            BackendPreference.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}
