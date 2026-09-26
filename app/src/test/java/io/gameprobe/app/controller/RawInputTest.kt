package io.gameprobe.app.controller

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawInputTest {

    @Test
    fun keyDownAndUpTrackHeldKeys() {
        val down = RawInputState().withKey(KeyEvent.KEYCODE_BUTTON_A, down = true)
        assertEquals(setOf(KeyEvent.KEYCODE_BUTTON_A), down.pressedKeys)
        assertTrue(down.withKey(KeyEvent.KEYCODE_BUTTON_A, down = false).pressedKeys.isEmpty())
    }

    @Test
    fun changedAxesReportsOnlyDifferences() {
        val state = RawInputState(axes = mapOf(MotionEvent.AXIS_X to 0.5f, MotionEvent.AXIS_Y to 0f))
        val changed = state.changedAxes(
            mapOf(MotionEvent.AXIS_X to 0.5f, MotionEvent.AXIS_Y to 0.2f, MotionEvent.AXIS_Z to 0f),
        )
        assertEquals(mapOf(MotionEvent.AXIS_Y to 0.2f, MotionEvent.AXIS_Z to 0f), changed)
    }

    private fun motion(device: Int, time: Long, vararg axes: Pair<Int, Float>) =
        RawMotionEvent(device, time, InputDevice.SOURCE_JOYSTICK, mapOf(*axes))

    private fun key(device: Int, time: Long) =
        RawKeyEvent(device, time, down = true, keyCode = KeyEvent.KEYCODE_BUTTON_R2, scanCode = 313, source = InputDevice.SOURCE_GAMEPAD)

    @Test
    fun consecutiveMotionFromSameDeviceMerges() {
        var log = RawEventLog.append(emptyList(), motion(8, 1, MotionEvent.AXIS_X to 0.1f))
        log = RawEventLog.append(log, motion(8, 2, MotionEvent.AXIS_X to 0.2f, MotionEvent.AXIS_RTRIGGER to 0.7f))
        assertEquals(1, log.size)
        val merged = log.single() as RawMotionEvent
        assertEquals(2, merged.samples)
        assertEquals(2L, merged.eventTimeMs)
        assertEquals(mapOf(MotionEvent.AXIS_X to 0.2f, MotionEvent.AXIS_RTRIGGER to 0.7f), merged.changedAxes)
    }

    @Test
    fun keyEventsAndOtherDevicesBreakMotionMerging() {
        var log = RawEventLog.append(emptyList(), motion(8, 1, MotionEvent.AXIS_X to 0.1f))
        log = RawEventLog.append(log, key(8, 2))
        log = RawEventLog.append(log, motion(8, 3, MotionEvent.AXIS_X to 0.2f))
        log = RawEventLog.append(log, motion(9, 4, MotionEvent.AXIS_X to 0.3f))
        assertEquals(4, log.size)
    }

    @Test
    fun logIsBoundedToCapacityKeepingNewest() {
        var log: List<RawEvent> = emptyList()
        for (t in 1L..10L) log = RawEventLog.append(log, key(8, t), capacity = 3)
        assertEquals(listOf(8L, 9L, 10L), log.map { it.eventTimeMs })
    }
}
