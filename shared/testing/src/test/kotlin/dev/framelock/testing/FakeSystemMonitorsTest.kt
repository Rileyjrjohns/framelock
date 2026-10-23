package dev.framelock.testing

import dev.framelock.live.domain.system.DeviceSnapshot
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FakeSystemMonitorsTest {
    @Test
    fun `thermal monitor starts at the initial level and follows set`() =
        runTest {
            val monitor = FakeThermalMonitor(ThermalLevel.LIGHT)
            val levels = mutableListOf<ThermalLevel>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.level.collect { levels += it } }

            monitor.set(ThermalLevel.SEVERE)
            monitor.set(ThermalLevel.NONE)

            assertEquals(listOf(ThermalLevel.LIGHT, ThermalLevel.SEVERE, ThermalLevel.NONE), levels)
            assertEquals(ThermalLevel.NONE, monitor.level.first())
        }

    @Test
    fun `thermal monitor defaults to no throttling`() =
        runTest {
            assertEquals(ThermalLevel.NONE, FakeThermalMonitor().level.first())
        }

    @Test
    fun `thermal failure ends the current collection and fails new collectors`() =
        runTest {
            val monitor = FakeThermalMonitor()
            val error = IllegalStateException("thermal service died")
            var caught: Throwable? = null
            launch(UnconfinedTestDispatcher(testScheduler)) {
                try {
                    monitor.level.collect {}
                } catch (e: IllegalStateException) {
                    caught = e
                }
            }

            monitor.fail(error)

            assertSame(error, caught)
            assertSame(error, assertThrows<IllegalStateException> { monitor.level.first() })
        }

    @Test
    fun `thermal monitor recovers when a level is set after a failure`() =
        runTest {
            val monitor = FakeThermalMonitor()
            monitor.fail(IllegalStateException("down"))

            monitor.set(ThermalLevel.MODERATE)

            assertEquals(ThermalLevel.MODERATE, monitor.level.first())
        }

    @Test
    fun `memory monitor starts at the initial pressure and follows set`() =
        runTest {
            val monitor = FakeMemoryPressureMonitor(MemoryPressure.MODERATE)
            val pressures = mutableListOf<MemoryPressure>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                monitor.pressure.collect { pressures += it }
            }

            monitor.set(MemoryPressure.CRITICAL)
            monitor.set(MemoryPressure.NORMAL)

            assertEquals(listOf(MemoryPressure.MODERATE, MemoryPressure.CRITICAL, MemoryPressure.NORMAL), pressures)
            assertEquals(MemoryPressure.NORMAL, FakeMemoryPressureMonitor().pressure.first())
        }

    @Test
    fun `memory failure ends the current collection and fails new collectors`() =
        runTest {
            val monitor = FakeMemoryPressureMonitor()
            val error = IllegalStateException("memory service died")
            var caught: Throwable? = null
            launch(UnconfinedTestDispatcher(testScheduler)) {
                try {
                    monitor.pressure.collect {}
                } catch (e: IllegalStateException) {
                    caught = e
                }
            }
            assertNull(caught)

            monitor.fail(error)

            assertNotNull(caught)
            assertSame(error, assertThrows<IllegalStateException> { monitor.pressure.first() })
            monitor.set(MemoryPressure.NORMAL)
            assertEquals(MemoryPressure.NORMAL, monitor.pressure.first())
        }

    @Test
    fun `device info returns a valid default snapshot that tests can replace`() {
        val provider = FakeDeviceInfoProvider()
        val default = provider.snapshot()

        assertEquals(ThermalLevel.NONE, default.thermalLevel)
        assertEquals(default, provider.snapshot)

        val hot = DeviceSnapshot("other-device", "15", 40, 41.5f, ThermalLevel.SEVERE)
        provider.snapshot = hot
        assertEquals(hot, provider.snapshot())
    }
}
