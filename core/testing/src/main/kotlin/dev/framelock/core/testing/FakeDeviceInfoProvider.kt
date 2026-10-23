package dev.framelock.core.testing

import dev.framelock.core.domain.port.DeviceInfoProvider
import dev.framelock.core.domain.system.DeviceSnapshot
import dev.framelock.core.domain.system.ThermalLevel

/** A [DeviceInfoProvider] that returns [snapshot], which tests may replace at any time. */
class FakeDeviceInfoProvider(
    @Volatile var snapshot: DeviceSnapshot = DEFAULT_SNAPSHOT,
) : DeviceInfoProvider {
    override fun snapshot(): DeviceSnapshot = snapshot

    private companion object {
        const val BATTERY_PCT = 80
        const val BATTERY_TEMP_C = 30f

        val DEFAULT_SNAPSHOT = DeviceSnapshot("test-device", "14", BATTERY_PCT, BATTERY_TEMP_C, ThermalLevel.NONE)
    }
}
