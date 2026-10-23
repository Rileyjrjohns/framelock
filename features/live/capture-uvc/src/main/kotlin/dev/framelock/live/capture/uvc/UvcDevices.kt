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

/** A UVC device as the source picker shows it: a [label] to read and an [id] to choose it by. */
data class UvcDeviceInfo(
    val id: String,
    val label: String,
)

/** What `UsbDevice` gives without the user's permission, as plain data so the naming rules are tested on the JVM. */
internal data class UsbDeviceFacts(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String?,
    val manufacturerName: String?,
)

private const val FALLBACK_LABEL = "USB video device"

/** Product name, else manufacturer name, else a generic label; blank strings count as missing. */
internal fun labelOf(facts: UsbDeviceFacts): String =
    facts.productName?.takeIf { it.isNotBlank() }
        ?: facts.manufacturerName?.takeIf { it.isNotBlank() }
        ?: FALLBACK_LABEL

/**
 * Names the devices, in the stable order of their system device name. The id is `vendor:product` in hex (`046d:085b`).
 * `UsbDevice.getSerialNumber()` would tell two identical devices apart across a re-plug, but it throws a
 * `SecurityException` for an app targeting API 29+ that has no permission on the device, and the picker lists devices
 * before any permission is asked. So identical devices share the id and get a `#2`, `#3` suffix in device-name order:
 * the first keeps the plain id, and which physical unit that is can change when the units are re-plugged.
 */
internal fun describeUvcDevices(devices: List<UsbDeviceFacts>): List<Pair<UsbDeviceFacts, UvcDeviceInfo>> {
    val seen = mutableMapOf<String, Int>()
    return devices.sortedBy { it.deviceName }.map { facts ->
        val base = "%04x:%04x".format(facts.vendorId, facts.productId)
        val rank = (seen[base] ?: 0) + 1
        seen[base] = rank
        facts to UvcDeviceInfo(if (rank == 1) base else "$base#$rank", labelOf(facts))
    }
}

/** The capture mode as the HUD shows it, for example `USB HD Webcam · MJPEG 1280x720@30`. Built once per stream. */
internal fun describeCapture(
    deviceLabel: String,
    mode: CaptureMode,
): String = "$deviceLabel · ${mode.format} ${mode.width}x${mode.height}@${mode.fps}"

/** UVC devices currently attached to the phone. */
object UvcDevices {
    private fun attached(context: Context): List<Pair<UsbDevice, UvcDeviceInfo>> {
        val devices =
            context
                .getSystemService(UsbManager::class.java)
                .deviceList.values
                .filter(::isUvc)
                .associateBy { it.deviceName }
        val facts = devices.values.map(::factsOf)
        return describeUvcDevices(facts).map { (fact, info) -> devices.getValue(fact.deviceName) to info }
    }

    /** The attached devices, for the picker. Listed afresh at each call: devices come and go. */
    fun list(context: Context): List<UvcDeviceInfo> = attached(context).map { it.second }

    /** The device with [id], or the first attached one when [id] is null. Null when it is not attached. */
    fun find(
        context: Context,
        id: String?,
    ): UsbDevice? = findWithInfo(context, id)?.first

    /** Same as [find], with the device's picker entry. */
    fun findWithInfo(
        context: Context,
        id: String?,
    ): Pair<UsbDevice, UvcDeviceInfo>? =
        attached(context).let { all ->
            if (id ==
                null
            ) {
                all.firstOrNull()
            } else {
                all.firstOrNull { it.second.id == id }
            }
        }

    fun isAttached(context: Context): Boolean = findWithInfo(context, null) != null

    /** True while [device] is still attached (by system device name, which is stable while plugged). */
    fun isStillAttached(
        context: Context,
        device: UsbDevice,
    ): Boolean = context.getSystemService(UsbManager::class.java).deviceList.containsKey(device.deviceName)

    internal fun isUvc(device: UsbDevice): Boolean =
        isUvcDevice(device.deviceClass, (0 until device.interfaceCount).map { device.getInterface(it).interfaceClass })

    private fun factsOf(device: UsbDevice) =
        UsbDeviceFacts(
            device.deviceName,
            device.vendorId,
            device.productId,
            device.productName,
            device.manufacturerName,
        )
}
