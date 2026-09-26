package io.gameprobe.app.controller

import android.view.InputDevice

/**
 * Pure helpers for classifying Android input devices. Everything here works on plain
 * integers so it can be unit tested without a device.
 */
object ControllerDetection {

    /**
     * Android source constants are bitmasks made of a "class" bit plus a device bit.
     * SOURCE_GAMEPAD (0x401) and SOURCE_KEYBOARD (0x101) share SOURCE_CLASS_BUTTON, so a
     * plain `sources and SOURCE_GAMEPAD != 0` check would wrongly match every keyboard.
     * The full mask must be present.
     */
    fun hasSource(sources: Int, source: Int): Boolean = (sources and source) == source

    /**
     * A device is treated as a game controller when Android reports it as a gamepad or a
     * joystick. D-pad alone is not enough: TV remotes, some keyboards and built-in navigation
     * keys also report SOURCE_DPAD. Device names are deliberately not consulted.
     */
    fun isGameController(sources: Int, isVirtual: Boolean): Boolean {
        if (isVirtual) return false
        return hasSource(sources, InputDevice.SOURCE_GAMEPAD) ||
            hasSource(sources, InputDevice.SOURCE_JOYSTICK)
    }

    private val sourceNames: List<Pair<String, Int>> = listOf(
        "KEYBOARD" to InputDevice.SOURCE_KEYBOARD,
        "DPAD" to InputDevice.SOURCE_DPAD,
        "GAMEPAD" to InputDevice.SOURCE_GAMEPAD,
        "TOUCHSCREEN" to InputDevice.SOURCE_TOUCHSCREEN,
        "MOUSE" to InputDevice.SOURCE_MOUSE,
        "STYLUS" to InputDevice.SOURCE_STYLUS,
        "BLUETOOTH_STYLUS" to 0x0000C002, // SOURCE_BLUETOOTH_STYLUS, API 23
        "TRACKBALL" to InputDevice.SOURCE_TRACKBALL,
        "MOUSE_RELATIVE" to 0x00020004, // SOURCE_MOUSE_RELATIVE, API 26
        "TOUCHPAD" to InputDevice.SOURCE_TOUCHPAD,
        "TOUCH_NAVIGATION" to InputDevice.SOURCE_TOUCH_NAVIGATION,
        "ROTARY_ENCODER" to 0x00400000, // SOURCE_ROTARY_ENCODER, API 26
        "JOYSTICK" to InputDevice.SOURCE_JOYSTICK,
        "HDMI" to 0x02000001, // SOURCE_HDMI, API 28
        "SENSOR" to 0x04000000, // SOURCE_SENSOR, API 31
    )

    /** Names of every known source fully contained in [sources]. */
    fun sourceNames(sources: Int): List<String> =
        sourceNames.filter { (_, mask) -> hasSource(sources, mask) }.map { it.first }

    fun formatSources(sources: Int): String {
        val names = sourceNames(sources)
        val hex = Format.hex32(sources)
        return if (names.isEmpty()) hex else "${names.joinToString(" | ")} ($hex)"
    }

    /** Android returns 0 for vendor/product IDs it does not know. */
    fun formatUsbId(id: Int): String = if (id == 0) UNKNOWN else Format.hex16(id)

    fun keyboardTypeName(type: Int): String = when (type) {
        InputDevice.KEYBOARD_TYPE_NONE -> "NONE"
        InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC -> "NON_ALPHABETIC"
        InputDevice.KEYBOARD_TYPE_ALPHABETIC -> "ALPHABETIC"
        else -> "$UNKNOWN ($type)"
    }

    const val UNKNOWN = "UNKNOWN"
}
