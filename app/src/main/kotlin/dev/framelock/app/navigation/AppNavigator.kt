package dev.framelock.app.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Commands survive a collector restart but are consumed only once. */
class AppNavigator {
    private val pending = Channel<AppRoutes>(Channel.UNLIMITED)
    val commands = pending.receiveAsFlow()

    fun go(route: AppRoutes) {
        pending.trySend(redirectOf(route)).getOrThrow()
    }
}
