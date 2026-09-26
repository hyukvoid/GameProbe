package io.gameprobe.app.controller

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerMapperTest {

    private val layout = AxisLayout.resolve(
        listOf(
            range(MotionEvent.AXIS_X, flat = 0.1f), range(MotionEvent.AXIS_Y, flat = 0.1f),
            range(MotionEvent.AXIS_Z, flat = 0.05f), range(MotionEvent.AXIS_RZ, flat = 0.05f),
            trigger(MotionEvent.AXIS_LTRIGGER), trigger(MotionEvent.AXIS_BRAKE),
            trigger(MotionEvent.AXIS_RTRIGGER), trigger(MotionEvent.AXIS_GAS),
            range(MotionEvent.AXIS_HAT_X), range(MotionEvent.AXIS_HAT_Y),
        ),
    )

    private fun keys(vararg codes: Int) = RawInputState(pressedKeys = codes.toSet())

    @Test
    fun faceButtonsFollowAndroidKeycodePositions() {
        assertEquals(setOf(Button.FACE_SOUTH), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(setOf(Button.FACE_EAST), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(setOf(Button.FACE_WEST), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(setOf(Button.FACE_NORTH), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_Y))
    }

    @Test
    fun shoulderStickClickAndSystemButtons() {
        assertEquals(setOf(Button.L1), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(setOf(Button.R1), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(setOf(Button.L3), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertEquals(setOf(Button.R3), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertEquals(setOf(Button.START), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(setOf(Button.SELECT), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(setOf(Button.SELECT), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BACK))
        assertEquals(setOf(Button.HOME), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_BUTTON_MODE))
    }

    @Test
    fun diagonalDpadKeycodesPressTwoDirections() {
        assertEquals(setOf(Button.DPAD_UP, Button.DPAD_LEFT), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_DPAD_UP_LEFT))
        assertEquals(setOf(Button.DPAD_DOWN, Button.DPAD_RIGHT), ControllerMapper.buttonsFor(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT))
    }

    @Test
    fun genericButtonsAreKeptAsUnmappedInsteadOfDropped() {
        val state = ControllerMapper.normalize(keys(KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_A), layout)
        assertEquals(setOf(Button.FACE_SOUTH), state.buttons)
        assertEquals(setOf(KeyEvent.KEYCODE_BUTTON_1), state.unmappedKeys)
    }

    @Test
    fun deadZoneZeroesValuesInsideFlatAndPassesOthersThrough() {
        assertEquals(0f, ControllerMapper.applyDeadZone(0.05f, 0.1f), 0f)
        assertEquals(0f, ControllerMapper.applyDeadZone(-0.1f, 0.1f), 0f)
        assertEquals(0.11f, ControllerMapper.applyDeadZone(0.11f, 0.1f), 0f)
        assertEquals(-0.9f, ControllerMapper.applyDeadZone(-0.9f, 0.1f), 0f)
        assertEquals(0.01f, ControllerMapper.applyDeadZone(0.01f, 0f), 0f)
    }

    @Test
    fun stickKeepsRawAndDeadZonedValues() {
        val raw = RawInputState(axes = mapOf(MotionEvent.AXIS_X to -0.03f, MotionEvent.AXIS_Y to 0.91f))
        val stick = ControllerMapper.normalize(raw, layout).leftStick
        assertEquals(-0.03f, stick.rawX, 0f)
        assertEquals(0f, stick.x, 0f)
        assertEquals(0.91f, stick.y, 0f)
        assertEquals(0.1f, stick.flatX, 0f)
        assertEquals(MotionEvent.AXIS_X, stick.axisX)
    }

    @Test
    fun rightStickReadsZAndRz() {
        val raw = RawInputState(axes = mapOf(MotionEvent.AXIS_Z to 0.5f, MotionEvent.AXIS_RZ to -0.7f))
        val stick = ControllerMapper.normalize(raw, layout).rightStick
        assertEquals(0.5f, stick.x, 0f)
        assertEquals(-0.7f, stick.y, 0f)
    }

    @Test
    fun triggerNormalizationUsesMotionRange() {
        assertEquals(0.73f, ControllerMapper.normalizeTrigger(0.73f, trigger(MotionEvent.AXIS_RTRIGGER)), 1e-6f)
        val bipolar = range(MotionEvent.AXIS_RTRIGGER, min = -1f, max = 1f)
        assertEquals(0f, ControllerMapper.normalizeTrigger(-1f, bipolar), 1e-6f)
        assertEquals(0.5f, ControllerMapper.normalizeTrigger(0f, bipolar), 1e-6f)
        assertEquals(1f, ControllerMapper.normalizeTrigger(1f, bipolar), 1e-6f)
        assertEquals(1f, ControllerMapper.normalizeTrigger(1.2f, trigger(MotionEvent.AXIS_RTRIGGER)), 0f)
    }

    @Test
    fun analogTriggerReadsPrimaryAxis() {
        val raw = RawInputState(axes = mapOf(MotionEvent.AXIS_RTRIGGER to 0.73f, MotionEvent.AXIS_GAS to 0.73f))
        val rt = ControllerMapper.normalize(raw, layout).rightTrigger
        assertEquals(0.73f, rt.value, 1e-6f)
        assertEquals(MotionEvent.AXIS_RTRIGGER, rt.axis)
        assertFalse(rt.digitalPressed)
    }

    @Test
    fun triggerUsesMirrorAxisWhenOnlyItMoves() {
        val raw = RawInputState(axes = mapOf(MotionEvent.AXIS_LTRIGGER to 0f, MotionEvent.AXIS_BRAKE to 0.4f))
        val lt = ControllerMapper.normalize(raw, layout).leftTrigger
        assertEquals(0.4f, lt.value, 1e-6f)
        assertEquals(MotionEvent.AXIS_BRAKE, lt.axis)
    }

    @Test
    fun digitalTriggerKeysAreReportedSeparatelyFromAnalogValue() {
        val state = ControllerMapper.normalize(keys(KeyEvent.KEYCODE_BUTTON_R2), layout)
        assertTrue(state.rightTrigger.digitalPressed)
        assertEquals(0f, state.rightTrigger.value, 0f)
        assertTrue(state.isPressed(Button.R2))
    }

    @Test
    fun digitalOnlyTriggerHasNoAxis() {
        val state = ControllerMapper.normalize(keys(KeyEvent.KEYCODE_BUTTON_L2), AxisLayout.EMPTY)
        assertTrue(state.leftTrigger.digitalPressed)
        assertNull(state.leftTrigger.axis)
        assertFalse(state.leftTrigger.hasAnalog)
    }

    @Test
    fun hatAxesDriveDpad() {
        val upLeft = RawInputState(axes = mapOf(MotionEvent.AXIS_HAT_X to -1f, MotionEvent.AXIS_HAT_Y to -1f))
        val state = ControllerMapper.normalize(upLeft, layout)
        assertEquals(setOf(Button.DPAD_UP, Button.DPAD_LEFT), state.buttons)
        assertEquals(setOf(DpadSource.HAT), state.dpadSources)

        val downRight = RawInputState(axes = mapOf(MotionEvent.AXIS_HAT_X to 1f, MotionEvent.AXIS_HAT_Y to 1f))
        assertEquals(setOf(Button.DPAD_DOWN, Button.DPAD_RIGHT), ControllerMapper.normalize(downRight, layout).buttons)

        val centered = RawInputState(axes = mapOf(MotionEvent.AXIS_HAT_X to 0f, MotionEvent.AXIS_HAT_Y to 0f))
        assertEquals(emptySet<Button>(), ControllerMapper.normalize(centered, layout).buttons)
    }

    @Test
    fun hatIgnoredWhenDeviceReportsNoHatRange() {
        val raw = RawInputState(axes = mapOf(MotionEvent.AXIS_HAT_X to -1f))
        assertEquals(emptySet<Button>(), ControllerMapper.normalize(raw, AxisLayout.EMPTY).buttons)
    }

    @Test
    fun dpadKeysAreTaggedWithKeySource() {
        val state = ControllerMapper.normalize(keys(KeyEvent.KEYCODE_DPAD_RIGHT), layout)
        assertEquals(setOf(Button.DPAD_RIGHT), state.buttons)
        assertEquals(setOf(DpadSource.KEYS), state.dpadSources)
    }
}
