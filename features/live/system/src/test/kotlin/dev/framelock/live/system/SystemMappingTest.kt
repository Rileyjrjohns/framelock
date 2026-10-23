package dev.framelock.live.system

import android.os.PowerManager
import dev.framelock.live.domain.system.ThermalLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SystemMappingTest {
    @Test
    fun `every platform thermal status maps to its level`() {
        val expected =
            mapOf(
                PowerManager.THERMAL_STATUS_NONE to ThermalLevel.NONE,
                PowerManager.THERMAL_STATUS_LIGHT to ThermalLevel.LIGHT,
                PowerManager.THERMAL_STATUS_MODERATE to ThermalLevel.MODERATE,
                PowerManager.THERMAL_STATUS_SEVERE to ThermalLevel.SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL to ThermalLevel.CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY to ThermalLevel.EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN to ThermalLevel.SHUTDOWN,
            )

        expected.forEach { (status, level) -> assertEquals(level, ThermalLevels.of(status)) }
    }

    @Test
    fun `an unknown status is treated as the worst level`() {
        assertEquals(ThermalLevel.SHUTDOWN, ThermalLevels.of(42))
    }

    @Test
    fun `device strings are reduced to the bounded token charset`() {
        assertEquals("Pixel 8 Pro", BoundedToken.of("Pixel 8 Pro"))
        assertEquals("Model_X-1.2", BoundedToken.of("Model_X-1.2™"))
        assertEquals("unknown", BoundedToken.of("™™"))
        assertEquals(64, BoundedToken.of("a".repeat(100)).length)
    }

    @Test
    fun `battery percentage is clamped and unknown values become 0`() {
        assertEquals(50, Battery.percent(level = 50, scale = 100))
        assertEquals(100, Battery.percent(level = 120, scale = 100))
        assertEquals(0, Battery.percent(level = -1, scale = 100))
        assertEquals(0, Battery.percent(level = 10, scale = 0))
    }
}
