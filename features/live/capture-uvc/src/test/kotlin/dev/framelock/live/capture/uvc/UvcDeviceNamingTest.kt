package dev.framelock.live.capture.uvc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UvcDeviceNamingTest {
    private fun facts(
        name: String = "/dev/bus/usb/001/002",
        vendor: Int = 0x046d,
        product: Int = 0x085b,
        productName: String? = "Test Webcam",
        manufacturer: String? = "Maker",
    ) = UsbDeviceFacts(name, vendor, product, productName, manufacturer)

    @Test
    fun `the label is the product name`() {
        assertEquals("Test Webcam", labelOf(facts()))
    }

    @Test
    fun `the label falls back to the manufacturer, then to a generic one`() {
        assertEquals("Maker", labelOf(facts(productName = null)))
        assertEquals("Maker", labelOf(facts(productName = "  ")))
        assertEquals("USB video device", labelOf(facts(productName = null, manufacturer = null)))
        assertEquals("USB video device", labelOf(facts(productName = "", manufacturer = " ")))
    }

    @Test
    fun `the id is vendor and product in padded hex`() {
        val described = describeUvcDevices(listOf(facts(vendor = 0x1, product = 0xabc)))

        assertEquals("0001:0abc", described.single().second.id)
    }

    @Test
    fun `identical devices get a suffix in device-name order`() {
        val described =
            describeUvcDevices(listOf(facts(name = "/dev/bus/usb/001/009"), facts(name = "/dev/bus/usb/001/003")))

        assertEquals(
            listOf("/dev/bus/usb/001/003" to "046d:085b", "/dev/bus/usb/001/009" to "046d:085b#2"),
            described.map { (f, info) -> f.deviceName to info.id },
        )
    }

    @Test
    fun `different devices keep plain ids`() {
        val described =
            describeUvcDevices(listOf(facts(name = "a"), facts(name = "b", product = 0x1)))

        assertEquals(listOf("046d:085b", "046d:0001"), described.map { it.second.id })
    }

    @Test
    fun `the capture description names device, format, size and rate`() {
        val mode = CaptureMode(CaptureFormat.MJPEG, 1280, 720, 30)

        assertEquals("USB HD Webcam · MJPEG 1280x720@30", describeCapture("USB HD Webcam", mode))
    }
}
