package io.gameprobe.app.controller

import android.view.KeyEvent
import kotlin.math.abs

/**
 * Deterministic translation from raw Android input to [ControllerState]. No Android
 * runtime calls, so it is fully unit tested.
 */
object ControllerMapper {

    /** HAT axes report -1, 0 or 1; anything past half-way counts as pressed. */
    const val HAT_THRESHOLD = 0.5f

    /**
     * Normalized buttons for an Android keycode. Diagonal D-pad keycodes (API 24) map to two
     * directions. KEYCODE_BACK maps to SELECT because some controllers expose their
     * Select/View button that way; the raw keycode stays visible in the raw log.
     */
    fun buttonsFor(keyCode: Int): Set<Button> = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> setOf(Button.FACE_SOUTH)
        KeyEvent.KEYCODE_BUTTON_B -> setOf(Button.FACE_EAST)
        KeyEvent.KEYCODE_BUTTON_X -> setOf(Button.FACE_WEST)
        KeyEvent.KEYCODE_BUTTON_Y -> setOf(Button.FACE_NORTH)
        KeyEvent.KEYCODE_BUTTON_L1 -> setOf(Button.L1)
        KeyEvent.KEYCODE_BUTTON_R1 -> setOf(Button.R1)
        KeyEvent.KEYCODE_BUTTON_L2 -> setOf(Button.L2)
        KeyEvent.KEYCODE_BUTTON_R2 -> setOf(Button.R2)
        KeyEvent.KEYCODE_BUTTON_THUMBL -> setOf(Button.L3)
        KeyEvent.KEYCODE_BUTTON_THUMBR -> setOf(Button.R3)
        KeyEvent.KEYCODE_BUTTON_START -> setOf(Button.START)
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> setOf(Button.SELECT)
        KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_HOME -> setOf(Button.HOME)
        KeyEvent.KEYCODE_DPAD_UP -> setOf(Button.DPAD_UP)
        KeyEvent.KEYCODE_DPAD_DOWN -> setOf(Button.DPAD_DOWN)
        KeyEvent.KEYCODE_DPAD_LEFT -> setOf(Button.DPAD_LEFT)
        KeyEvent.KEYCODE_DPAD_RIGHT -> setOf(Button.DPAD_RIGHT)
        KeyEvent.KEYCODE_DPAD_UP_LEFT -> setOf(Button.DPAD_UP, Button.DPAD_LEFT)
        KeyEvent.KEYCODE_DPAD_UP_RIGHT -> setOf(Button.DPAD_UP, Button.DPAD_RIGHT)
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> setOf(Button.DPAD_DOWN, Button.DPAD_LEFT)
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> setOf(Button.DPAD_DOWN, Button.DPAD_RIGHT)
        else -> emptySet()
    }

    private val DPAD_BUTTONS = setOf(Button.DPAD_UP, Button.DPAD_DOWN, Button.DPAD_LEFT, Button.DPAD_RIGHT)

    /**
     * Android's documented dead-zone handling: values inside the axis's `flat` region are
     * treated as centered, values outside are passed through unchanged.
     */
    fun applyDeadZone(value: Float, flat: Float): Float =
        if (abs(value) > flat) value else 0f

    /** Map a trigger axis value onto 0..1 using its reported MotionRange. */
    fun normalizeTrigger(value: Float, range: AxisRange): Float {
        val span = range.max - range.min
        val normalized = if (span > 0f) (value - range.min) / span else value
        return normalized.coerceIn(0f, 1f)
    }

    fun normalize(raw: RawInputState, layout: AxisLayout): ControllerState {
        val buttons = HashSet<Button>()
        val unmapped = HashSet<Int>()
        for (keyCode in raw.pressedKeys) {
            val mapped = buttonsFor(keyCode)
            if (mapped.isEmpty()) unmapped += keyCode else buttons += mapped
        }

        val dpadSources = HashSet<DpadSource>()
        if (buttons.any { it in DPAD_BUTTONS }) dpadSources += DpadSource.KEYS

        val hatX = layout.hatX?.let { raw.axes[it.axis] } ?: 0f
        val hatY = layout.hatY?.let { raw.axes[it.axis] } ?: 0f
        val hatButtons = buildSet {
            if (hatX <= -HAT_THRESHOLD) add(Button.DPAD_LEFT)
            if (hatX >= HAT_THRESHOLD) add(Button.DPAD_RIGHT)
            // Android HAT_Y is negative for up.
            if (hatY <= -HAT_THRESHOLD) add(Button.DPAD_UP)
            if (hatY >= HAT_THRESHOLD) add(Button.DPAD_DOWN)
        }
        if (hatButtons.isNotEmpty()) {
            dpadSources += DpadSource.HAT
            buttons += hatButtons
        }

        return ControllerState(
            buttons = buttons,
            leftStick = stick(raw, layout.leftX, layout.leftY),
            rightStick = stick(raw, layout.rightX, layout.rightY),
            leftTrigger = trigger(raw, layout.leftTrigger, KeyEvent.KEYCODE_BUTTON_L2),
            rightTrigger = trigger(raw, layout.rightTrigger, KeyEvent.KEYCODE_BUTTON_R2),
            hatX = hatX,
            hatY = hatY,
            dpadSources = dpadSources,
            unmappedKeys = unmapped,
        )
    }

    private fun stick(raw: RawInputState, xRange: AxisRange?, yRange: AxisRange?): StickState {
        val rawX = xRange?.let { raw.axes[it.axis] } ?: 0f
        val rawY = yRange?.let { raw.axes[it.axis] } ?: 0f
        val flatX = xRange?.flat ?: 0f
        val flatY = yRange?.flat ?: 0f
        return StickState(
            rawX = rawX,
            rawY = rawY,
            x = applyDeadZone(rawX, flatX),
            y = applyDeadZone(rawY, flatY),
            flatX = flatX,
            flatY = flatY,
            axisX = xRange?.axis,
            axisY = yRange?.axis,
        )
    }

    private fun trigger(raw: RawInputState, candidates: List<AxisRange>, keyCode: Int): TriggerState {
        val digital = keyCode in raw.pressedKeys
        if (candidates.isEmpty()) return TriggerState(digitalPressed = digital)
        // Android mirrors LTRIGGER->BRAKE and RTRIGGER->GAS, but not every driver fills both.
        // Take the largest so a device that only updates one of them still reads correctly.
        var best = candidates.first()
        var bestValue = normalizeTrigger(raw.axes[best.axis] ?: best.min, best)
        for (candidate in candidates.drop(1)) {
            val value = normalizeTrigger(raw.axes[candidate.axis] ?: candidate.min, candidate)
            if (value > bestValue) {
                best = candidate
                bestValue = value
            }
        }
        return TriggerState(value = bestValue, axis = best.axis, digitalPressed = digital)
    }
}
