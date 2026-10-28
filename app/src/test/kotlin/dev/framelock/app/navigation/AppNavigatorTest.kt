package dev.framelock.app.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigatorTest {
    @Test fun `both destinations remain reachable`() {
        assertEquals(AppRoutes.Live, redirectOf(AppRoutes.Live))
        assertEquals(AppRoutes.Models, redirectOf(AppRoutes.Models))
    }

    @Test fun `commands emitted before collection keep their order`() =
        runTest {
            val navigator = AppNavigator()
            navigator.go(AppRoutes.Models)
            navigator.go(AppRoutes.Live)
            assertEquals(listOf(AppRoutes.Models, AppRoutes.Live), navigator.commands.take(2).toList())
        }

    @Test fun `restarting collection does not replay a consumed command`() =
        runTest {
            val navigator = AppNavigator()
            navigator.go(AppRoutes.Models)
            assertEquals(AppRoutes.Models, navigator.commands.first())
            var received = false
            val collector = backgroundScope.launch { navigator.commands.collect { received = true } }
            runCurrent()
            assertFalse(received)
            collector.cancel()
            navigator.go(AppRoutes.Live)
            assertEquals(AppRoutes.Live, navigator.commands.first())
        }
}
