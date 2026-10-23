package dev.framelock.live.domain.port

interface Tracer

interface ThermalMonitor

interface MemoryPressureMonitor

interface DeviceInfoProvider

interface Clock

interface FrameSource

fun interface TelemetrySink {
    fun record(event: Any)
}

sealed interface SourceEvent {
    data object Disconnected : SourceEvent
}

data class Connected(val id: Int) : SourceEvent
