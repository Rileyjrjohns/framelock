package dev.framelock.live.ui.components

import java.util.Locale

internal fun Enum<*>.label(): String = name.lowercase(Locale.ROOT).replace('_', ' ')
