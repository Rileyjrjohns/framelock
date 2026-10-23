package dev.framelock.live.capture.uvc

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UvcDeviceClassTest {
    @Test
    fun `a device of the video class is a UVC device`() {
        assertTrue(isUvcDevice(deviceClass = 0x0E, interfaceClasses = emptyList()))
    }

    @Test
    fun `a composite device with a video interface is a UVC device`() {
        assertTrue(isUvcDevice(deviceClass = 0xEF, interfaceClasses = listOf(0x0E, 0x0E, 0x01)))
        assertTrue(isUvcDevice(deviceClass = 0x00, interfaceClasses = listOf(0x01, 0x0E)))
    }

    @Test
    fun `a miscellaneous device without a video interface is not`() {
        assertFalse(isUvcDevice(deviceClass = 0xEF, interfaceClasses = listOf(0x01, 0x03)))
    }

    @Test
    fun `a storage or hub device is not`() {
        assertFalse(isUvcDevice(deviceClass = 0x09, interfaceClasses = listOf(0x09)))
        assertFalse(isUvcDevice(deviceClass = 0x00, interfaceClasses = listOf(0x08)))
    }
}
