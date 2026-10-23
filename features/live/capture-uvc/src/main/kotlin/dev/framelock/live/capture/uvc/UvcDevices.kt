package dev.framelock.live.capture.uvc

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/** USB video class (UVC), the class of capture cards and webcams. */
private const val USB_CLASS_VIDEO = 0x0E

/**
 * A device is UVC when it declares the video class itself, or has a video interface (the usual case for a composite
 * device announced with an interface association, device class 0xEF). Pure, so it is tested on the JVM.
 */
internal fun isUvcDevice(
    deviceClass: Int,
    interfaceClasses: List<Int>,
): Boolean = deviceClass == USB_CLASS_VIDEO || USB_CLASS_VIDEO in interfaceClasses

/** UVC devices currently attached to the phone. */
object UvcDevices {
    /** The first attached UVC device, by device name so that the choice is stable. */
    fun firstAttached(context: Context): UsbDevice? =
        context
            .getSystemService(UsbManager::class.java)
            .deviceList.values
            .filter(::isUvc)
            .minByOrNull { it.deviceName }

    fun isAttached(context: Context): Boolean = firstAttached(context) != null

    internal fun isUvc(device: UsbDevice): Boolean =
        isUvcDevice(device.deviceClass, (0 until device.interfaceCount).map { device.getInterface(it).interfaceClass })
}
