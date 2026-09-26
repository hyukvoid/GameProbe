package io.gameprobe.app.controller

import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun range(axis: Int, min: Float = -1f, max: Float = 1f, flat: Float = 0f) =
    AxisRange(axis, InputDevice.SOURCE_JOYSTICK, min, max, flat, fuzz = 0f, resolution = 0f)

internal fun trigger(axis: Int) = range(axis, min = 0f, max = 1f)

class AxisLayoutTest {

    private val standard = listOf(
        range(MotionEvent.AXIS_X), range(MotionEvent.AXIS_Y),
        range(MotionEvent.AXIS_Z), range(MotionEvent.AXIS_RZ),
        trigger(MotionEvent.AXIS_LTRIGGER), trigger(MotionEvent.AXIS_RTRIGGER),
        trigger(MotionEvent.AXIS_BRAKE), trigger(MotionEvent.AXIS_GAS),
        range(MotionEvent.AXIS_HAT_X), range(MotionEvent.AXIS_HAT_Y),
    )

    @Test
    fun standardProfileMapsEveryAxisWithoutNotes() {
        val layout = AxisLayout.resolve(standard)
        assertEquals(MotionEvent.AXIS_X, layout.leftX?.axis)
        assertEquals(MotionEvent.AXIS_Y, layout.leftY?.axis)
        assertEquals(MotionEvent.AXIS_Z, layout.rightX?.axis)
        assertEquals(MotionEvent.AXIS_RZ, layout.rightY?.axis)
        assertEquals(listOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE), layout.leftTrigger.map { it.axis })
        assertEquals(listOf(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS), layout.rightTrigger.map { it.axis })
        assertEquals(MotionEvent.AXIS_HAT_X, layout.hatX?.axis)
        assertEquals(MotionEvent.AXIS_HAT_Y, layout.hatY?.axis)
        assertEquals(emptyList<LayoutNote>(), layout.notes)
    }

    @Test
    fun rightStickFallsBackToRxRyOnlyWhenZRzAbsent() {
        val layout = AxisLayout.resolve(
            listOf(
                range(MotionEvent.AXIS_X), range(MotionEvent.AXIS_Y),
                range(MotionEvent.AXIS_RX), range(MotionEvent.AXIS_RY),
            ),
        )
        assertEquals(MotionEvent.AXIS_RX, layout.rightX?.axis)
        assertEquals(MotionEvent.AXIS_RY, layout.rightY?.axis)
        assertTrue(LayoutNote.RightStickOnRxRy in layout.notes)
        assertTrue(LayoutNote.NoAnalogTriggers in layout.notes)
    }

    @Test
    fun rxRyAreReportedAsUnmappedWhenZRzPresent() {
        val layout = AxisLayout.resolve(standard + range(MotionEvent.AXIS_RX) + range(MotionEvent.AXIS_RY))
        assertEquals(MotionEvent.AXIS_Z, layout.rightX?.axis)
        assertEquals(
            listOf(LayoutNote.UnmappedAxes(listOf(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY))),
            layout.notes,
        )
    }

    @Test
    fun deviceWithOnlyHatAxesHasNoSticks() {
        val layout = AxisLayout.resolve(listOf(range(MotionEvent.AXIS_HAT_X), range(MotionEvent.AXIS_HAT_Y)))
        assertNull(layout.leftX)
        assertNull(layout.rightX)
        assertTrue(LayoutNote.NoLeftStick in layout.notes)
        assertTrue(LayoutNote.NoRightStick in layout.notes)
    }

    @Test
    fun onlyBrakeAndGasStillProvideTriggers() {
        val layout = AxisLayout.resolve(listOf(trigger(MotionEvent.AXIS_BRAKE), trigger(MotionEvent.AXIS_GAS)))
        assertEquals(listOf(MotionEvent.AXIS_BRAKE), layout.leftTrigger.map { it.axis })
        assertEquals(listOf(MotionEvent.AXIS_GAS), layout.rightTrigger.map { it.axis })
    }

    @Test
    fun duplicateAxisRangesUseTheFirst() {
        val layout = AxisLayout.resolve(listOf(range(MotionEvent.AXIS_X, flat = 0.1f), range(MotionEvent.AXIS_X, flat = 0.5f)))
        assertEquals(0.1f, layout.leftX!!.flat, 0f)
    }
}
