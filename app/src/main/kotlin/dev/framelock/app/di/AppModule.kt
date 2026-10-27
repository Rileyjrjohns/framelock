package dev.framelock.app.di

import dev.framelock.app.ApplicationStartup
import dev.framelock.app.DebugLaunchHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** All production definitions, also verified by the JVM graph test. */
internal val appModule =
    module {
        includes(systemModule, telemetryModule, liveModule, modelsModule)
        singleOf(::ApplicationStartup)
        singleOf(::DebugLaunchHandler)
    }
