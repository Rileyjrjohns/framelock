package dev.framelock.app.navigation

// Model actions enforce their own policy; both destinations remain reachable.
internal fun redirectOf(route: AppRoutes): AppRoutes = route
