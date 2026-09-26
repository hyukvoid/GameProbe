package io.gameprobe.app.controller

import android.view.InputDevice

/** One Android `InputDevice.MotionRange`, copied so it can be inspected and tested. */
data class AxisRange(
    val axis: Int,
    val source: Int,
    val min: Float,
    val max: Float,
    val flat: Float,
    val fuzz: Float,
    val resolution: Float,
)

/**
 * How the controller is attached. Android has no public API that reports the bus
 * (USB vs Bluetooth) for an InputDevice, so this stays UNKNOWN until it is supplied
 * by some other verified means (for example, a tester's report).
 */
enum class ConnectionType { UNKNOWN, USB, BLUETOOTH, OTHER }

/** Snapshot of the metadata Android exposes for one input device. */
data class ControllerInfo(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val keyboardType: Int,
    val controllerNumber: Int,
    /** `InputDevice.isExternal`, null below API 29 where it is not public. */
    val isExternal: Boolean?,
    val isVirtual: Boolean,
    /** Null when the platform cannot tell us. */
    val hasVibrator: Boolean?,
    /** Null when the platform cannot tell us (below API 31). */
    val hasBattery: Boolean?,
    val motionRanges: List<AxisRange>,
    /** Gamepad keycodes the device's key layout declares via `InputDevice.hasKeys`. */
    val declaredKeys: List<Int>,
    val connectionType: ConnectionType = ConnectionType.UNKNOWN,
    /**
     * Vendor-specific controller mode (e.g. "XInput", "DInput", "Switch"). Android does
     * not expose this; it is reserved for tester-supplied data and stays null here.
     */
    val inputMode: String? = null,
) {
    val isGameController: Boolean
        get() = ControllerDetection.isGameController(sources, isVirtual)

    /** Joystick axes reported by the device. These are the axes GameProbe reads. */
    val joystickRanges: List<AxisRange>
        get() = motionRanges.filter {
            ControllerDetection.hasSource(it.source, InputDevice.SOURCE_JOYSTICK)
        }
}
