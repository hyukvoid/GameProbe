package io.gameprobe.app.controller

import android.view.MotionEvent

/** Observations about how a device's reported axes were interpreted. */
sealed interface LayoutNote {
    data object NoLeftStick : LayoutNote
    data object NoRightStick : LayoutNote

    /** AXIS_Z/AXIS_RZ are absent, so AXIS_RX/AXIS_RY were used for the right stick. */
    data object RightStickOnRxRy : LayoutNote

    /** No analog trigger axes. Triggers may still arrive as KEYCODE_BUTTON_L2/R2. */
    data object NoAnalogTriggers : LayoutNote

    /** Axes the device reports that GameProbe does not map to a normalized control. */
    data class UnmappedAxes(val axes: List<Int>) : LayoutNote
}

/**
 * Which reported axes feed which normalized control, resolved from the device's own
 * joystick MotionRanges rather than from its name.
 *
 * Priorities follow Android's documented game controller profile: left stick AXIS_X/Y,
 * right stick AXIS_Z/RZ, triggers AXIS_LTRIGGER/RTRIGGER (mirrored by AXIS_BRAKE/GAS),
 * D-pad AXIS_HAT_X/Y. Anything else is reported as unmapped instead of guessed.
 */
data class AxisLayout(
    val leftX: AxisRange? = null,
    val leftY: AxisRange? = null,
    val rightX: AxisRange? = null,
    val rightY: AxisRange? = null,
    /** Candidate axes in priority order; the largest value wins. */
    val leftTrigger: List<AxisRange> = emptyList(),
    val rightTrigger: List<AxisRange> = emptyList(),
    val hatX: AxisRange? = null,
    val hatY: AxisRange? = null,
    val notes: List<LayoutNote> = emptyList(),
) {
    companion object {
        val EMPTY = AxisLayout()

        fun resolve(joystickRanges: List<AxisRange>): AxisLayout {
            val byAxis = LinkedHashMap<Int, AxisRange>()
            for (range in joystickRanges) byAxis.putIfAbsent(range.axis, range)

            val leftX = byAxis[MotionEvent.AXIS_X]
            val leftY = byAxis[MotionEvent.AXIS_Y]

            val zPair = byAxis[MotionEvent.AXIS_Z] to byAxis[MotionEvent.AXIS_RZ]
            val rPair = byAxis[MotionEvent.AXIS_RX] to byAxis[MotionEvent.AXIS_RY]
            val useRxRy = zPair.first == null && zPair.second == null &&
                (rPair.first != null || rPair.second != null)
            val (rightX, rightY) = if (useRxRy) rPair else zPair

            val leftTrigger = listOfNotNull(
                byAxis[MotionEvent.AXIS_LTRIGGER],
                byAxis[MotionEvent.AXIS_BRAKE],
            )
            val rightTrigger = listOfNotNull(
                byAxis[MotionEvent.AXIS_RTRIGGER],
                byAxis[MotionEvent.AXIS_GAS],
            )
            val hatX = byAxis[MotionEvent.AXIS_HAT_X]
            val hatY = byAxis[MotionEvent.AXIS_HAT_Y]

            val used = (listOfNotNull(leftX, leftY, rightX, rightY, hatX, hatY) +
                leftTrigger + rightTrigger).map { it.axis }.toSet()
            val unmapped = byAxis.keys.filter { it !in used }

            val notes = buildList {
                if (leftX == null && leftY == null) add(LayoutNote.NoLeftStick)
                if (rightX == null && rightY == null) add(LayoutNote.NoRightStick)
                if (useRxRy) add(LayoutNote.RightStickOnRxRy)
                if (leftTrigger.isEmpty() && rightTrigger.isEmpty()) add(LayoutNote.NoAnalogTriggers)
                if (unmapped.isNotEmpty()) add(LayoutNote.UnmappedAxes(unmapped))
            }

            return AxisLayout(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, hatX, hatY, notes)
        }
    }
}
