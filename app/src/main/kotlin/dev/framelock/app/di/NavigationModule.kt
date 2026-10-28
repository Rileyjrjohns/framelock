package dev.framelock.app.di

import dev.framelock.app.navigation.AppNavigator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val navigationModule = module { singleOf(::AppNavigator) }
