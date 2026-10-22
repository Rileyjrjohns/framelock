package dev.framelock.app

import dev.framelock.core.domain.port.Clock as PortClock
import dev.framelock.core.domain.port.DeviceInfoProvider
import dev.framelock.core.domain.port.Tracer

object AppGraph {
    val tracer = object : Tracer {}
}

interface AppDeviceInfo : DeviceInfoProvider

class IndirectDeviceInfo : AppDeviceInfo

class AliasedClock : PortClock
