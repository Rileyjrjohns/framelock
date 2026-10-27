package dev.framelock.app.di

import dev.framelock.app.AppEnvironment
import dev.framelock.app.BackendOverride
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.Tracer
import dev.framelock.live.domain.port.DeviceInfoProvider
import dev.framelock.live.domain.port.MemoryPressureMonitor
import dev.framelock.live.domain.port.ThermalMonitor
import dev.framelock.live.system.AndroidClock
import dev.framelock.live.system.AndroidDeviceInfoProvider
import dev.framelock.live.system.AndroidMemoryPressureMonitor
import dev.framelock.live.system.AndroidTracer
import dev.framelock.live.system.PowerManagerThermalMonitor
import dev.framelock.live.system.SharedMemoryPressureMonitor
import dev.framelock.live.system.SharedThermalMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val systemModule =
    module {
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        singleOf(::AppEnvironment)
        singleOf(::BackendOverride)
        single { AndroidClock() } bind Clock::class
        single { AndroidTracer() } bind Tracer::class
        single { SharedThermalMonitor(PowerManagerThermalMonitor(get()), get()) } bind ThermalMonitor::class
        single { SharedMemoryPressureMonitor(AndroidMemoryPressureMonitor(get(), get()), get()) } bind
            MemoryPressureMonitor::class
        single { AndroidDeviceInfoProvider(get()) } bind DeviceInfoProvider::class
    }
