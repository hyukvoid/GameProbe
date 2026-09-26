package io.gameprobe.app.controller

/**
 * Normalized GameProbe controls. Face buttons are named by position following Android's
 * KEYCODE_BUTTON_A/B/X/Y convention (A = south, B = east, X = west, Y = north). The label
 * printed on a physical controller may differ; the raw keycode is always kept alongside.
 */
enum class Button {
    FACE_SOUTH, FACE_EAST, FACE_WEST, FACE_NORTH,
    L1, R1,
    /** Digital trigger keys (KEYCODE_BUTTON_L2/R2). Analog values live in [TriggerState]. */
    L2, R2,
    L3, R3,
    START, SELECT, HOME,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
}

/** Which Android representation produced the current D-pad state. */
enum class DpadSource { KEYS, HAT }

/**
 * Exactly what Android has reported for one device: currently held keycodes and the latest
 * value of every joystick axis. Normalization reads from this but never replaces it.
 */
data class RawInputState(
    val pressedKeys: Set<Int> = emptySet(),
    val axes: Map<Int, Float> = emptyMap(),
) {
    fun withKey(keyCode: Int, down: Boolean): RawInputState =
        copy(pressedKeys = if (down) pressedKeys + keyCode else pressedKeys - keyCode)

    fun withAxes(values: Map<Int, Float>): RawInputState = copy(axes = axes + values)

    /** Axes whose value in [values] differs from the current state. */
    fun changedAxes(values: Map<Int, Float>): Map<Int, Float> =
        values.filter { (axis, value) -> axes[axis] != value }
}

data class StickState(
    val rawX: Float = 0f,
    val rawY: Float = 0f,
    /** Value after the MotionRange.flat dead zone is applied. */
    val x: Float = 0f,
    val y: Float = 0f,
    val flatX: Float = 0f,
    val flatY: Float = 0f,
    val axisX: Int? = null,
    val axisY: Int? = null,
) {
    val available: Boolean get() = axisX != null || axisY != null
}

data class TriggerState(
    /** 0.0 released .. 1.0 fully pressed, normalized from the axis MotionRange. */
    val value: Float = 0f,
    /** Axis that produced [value], or null if the device reports no analog trigger axis. */
    val axis: Int? = null,
    /** Whether the digital KEYCODE_BUTTON_L2/R2 is currently held. */
    val digitalPressed: Boolean = false,
) {
    val hasAnalog: Boolean get() = axis != null
}

/** Normalized control state derived from [RawInputState] and an [AxisLayout]. */
data class ControllerState(
    val buttons: Set<Button> = emptySet(),
    val leftStick: StickState = StickState(),
    val rightStick: StickState = StickState(),
    val leftTrigger: TriggerState = TriggerState(),
    val rightTrigger: TriggerState = TriggerState(),
    val hatX: Float = 0f,
    val hatY: Float = 0f,
    val dpadSources: Set<DpadSource> = emptySet(),
    /** Held keycodes that have no normalized [Button]. Kept so nothing is silently dropped. */
    val unmappedKeys: Set<Int> = emptySet(),
) {
    fun isPressed(button: Button): Boolean = button in buttons
}
