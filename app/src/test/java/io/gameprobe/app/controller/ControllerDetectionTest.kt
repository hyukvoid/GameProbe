package io.gameprobe.app.controller

import android.view.InputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerDetectionTest {

    @Test
    fun gamepadSourceIsController() {
        assertTrue(ControllerDetection.isGameController(InputDevice.SOURCE_GAMEPAD, isVirtual = false))
    }

    @Test
    fun joystickOnlySourceIsController() {
        assertTrue(ControllerDetection.isGameController(InputDevice.SOURCE_JOYSTICK, isVirtual = false))
    }

    @Test
    fun typicalControllerSourceCombinationIsController() {
        val sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD or
            InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        assertTrue(ControllerDetection.isGameController(sources, isVirtual = false))
    }

    @Test
    fun keyboardIsNotControllerDespiteSharedButtonClassBit() {
        // SOURCE_KEYBOARD and SOURCE_GAMEPAD share SOURCE_CLASS_BUTTON; a partial mask match is wrong.
        assertFalse(ControllerDetection.isGameController(InputDevice.SOURCE_KEYBOARD, isVirtual = false))
    }

    @Test
    fun dpadOnlyDeviceIsNotController() {
        val sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD
        assertFalse(ControllerDetection.isGameController(sources, isVirtual = false))
    }

    @Test
    fun touchscreenAndMouseAreNotControllers() {
        assertFalse(ControllerDetection.isGameController(InputDevice.SOURCE_TOUCHSCREEN, isVirtual = false))
        assertFalse(ControllerDetection.isGameController(InputDevice.SOURCE_MOUSE, isVirtual = false))
    }

    @Test
    fun virtualDeviceIsNeverController() {
        assertFalse(ControllerDetection.isGameController(InputDevice.SOURCE_GAMEPAD, isVirtual = true))
    }

    @Test
    fun sourceNamesListOnlyFullyPresentSources() {
        assertEquals(listOf("KEYBOARD"), ControllerDetection.sourceNames(InputDevice.SOURCE_KEYBOARD))
        val sources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        assertEquals(listOf("GAMEPAD", "JOYSTICK"), ControllerDetection.sourceNames(sources))
    }

    @Test
    fun formatSourcesIncludesHex() {
        assertEquals("GAMEPAD (0x00000401)", ControllerDetection.formatSources(InputDevice.SOURCE_GAMEPAD))
        assertEquals("0x00000000", ControllerDetection.formatSources(0))
    }

    @Test
    fun zeroUsbIdIsUnknown() {
        assertEquals("UNKNOWN", ControllerDetection.formatUsbId(0))
        assertEquals("0x054C", ControllerDetection.formatUsbId(0x054C))
        assertEquals("0x0CE6", ControllerDetection.formatUsbId(0x0CE6))
    }
}
