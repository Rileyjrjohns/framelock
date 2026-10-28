package dev.framelock.app.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoutes {
    @Serializable data object Live : AppRoutes

    @Serializable data object Models : AppRoutes
}
