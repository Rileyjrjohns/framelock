package dev.framelock.app

import dev.framelock.live.inference.litert.BackendPreference

internal class BackendOverride {
    @Volatile var preference = BackendPreference.AUTO
}
