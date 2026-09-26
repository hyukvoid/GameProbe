package io.gameprobe.app.controller

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Live input for one connected controller. Fields are Compose snapshot state. */
class DeviceSession(info: ControllerInfo) {
    var info by mutableStateOf(info)
        internal set
    var layout by mutableStateOf(AxisLayout.resolve(info.joystickRanges))
        internal set
    var raw by mutableStateOf(RawInputState())
        internal set
    var state by mutableStateOf(ControllerState())
        internal set

    /** Keycodes seen from this device that have no normalized mapping. */
    var unmappedKeysSeen by mutableStateOf<Set<Int>>(emptySet())
        internal set

    internal fun update(newRaw: RawInputState) {
        raw = newRaw
        state = ControllerMapper.normalize(newRaw, layout)
    }
}

/**
 * Owns controller discovery, hot plugging and event intake. Must be driven from the main
 * thread: the activity forwards its key and generic motion events here, and the
 * InputDeviceListener is registered on the main looper.
 */
class ControllerProbe(context: Context) : InputManager.InputDeviceListener {

    private val inputManager: InputManager = requireNotNull(context.getSystemService(InputManager::class.java)) {
        "InputManager unavailable"
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = HashMap<Int, DeviceSession>()
    private var nonControllerIds: Set<Int> = emptySet()
    private var listening = false

    /** Descriptor of the last selected controller, used to reselect it after a reconnect. */
    private var lastSelectedDescriptor: String? = null

    var controllers by mutableStateOf<List<ControllerInfo>>(emptyList())
        private set

    /** Input devices that are not treated as controllers, shown for diagnosis. */
    var otherDevices by mutableStateOf<List<ControllerInfo>>(emptyList())
        private set

    var selected by mutableStateOf<DeviceSession?>(null)
        private set

    /** Recent raw events from all controllers, oldest first. */
    var rawLog by mutableStateOf<List<RawEvent>>(emptyList())
        private set

    fun start() {
        if (!listening) {
            inputManager.registerInputDeviceListener(this, mainHandler)
            listening = true
        }
        refreshDevices()
    }

    fun stop() {
        if (listening) {
            inputManager.unregisterInputDeviceListener(this)
            listening = false
        }
        releaseAllInputs()
    }

    fun select(deviceId: Int) {
        val session = sessions[deviceId] ?: return
        if (selected !== session) {
            selected = session
            lastSelectedDescriptor = session.info.descriptor
            Log.i(TAG, "Selected controller id=${session.info.id} name=\"${session.info.name}\"")
        }
    }

    fun clearRawLog() {
        rawLog = emptyList()
    }

    /**
     * Forget held keys. Called when the window loses focus or the app stops, because the
     * matching key-up events will be delivered elsewhere and would otherwise leave buttons
     * stuck in the pressed state. Axis values are kept; the next MotionEvent refreshes them.
     */
    fun releaseAllInputs() {
        for (session in sessions.values) {
            if (session.raw.pressedKeys.isNotEmpty()) {
                session.update(session.raw.copy(pressedKeys = emptySet()))
            }
        }
    }

    /** Returns true when the event came from a controller and was consumed by the probe. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in PASS_THROUGH_KEYS) return false
        val session = sessionFor(event.deviceId) ?: return false

        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true
        }
        // Android repeats held keys like a keyboard. Repeats carry no new information.
        if (down && event.repeatCount > 0) return true

        session.update(session.raw.withKey(event.keyCode, down))
        appendRaw(
            RawKeyEvent(
                deviceId = event.deviceId,
                eventTimeMs = event.eventTime,
                down = down,
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                source = event.source,
            ),
        )
        Log.d(
            TAG,
            "Key ${if (down) "DOWN" else "UP"} ${KeyEvent.keyCodeToString(event.keyCode)} " +
                "scan=${event.scanCode} device=${event.deviceId} source=${Format.hex32(event.source)}",
        )

        if (down && ControllerMapper.buttonsFor(event.keyCode).isEmpty() &&
            event.keyCode !in session.unmappedKeysSeen
        ) {
            session.unmappedKeysSeen = session.unmappedKeysSeen + event.keyCode
            Log.w(
                TAG,
                "Mapping: ${KeyEvent.keyCodeToString(event.keyCode)} from device ${event.deviceId} " +
                    "has no normalized control",
            )
        }
        return true
    }

    /** Returns true when the event is joystick input from a controller. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
        val session = sessionFor(event.deviceId) ?: return false

        val ranges = session.info.joystickRanges
        val values = HashMap<Int, Float>(ranges.size)
        for (range in ranges) values[range.axis] = event.getAxisValue(range.axis)

        val changed = session.raw.changedAxes(values)
        if (changed.isNotEmpty()) {
            session.update(session.raw.withAxes(values))
            appendRaw(
                RawMotionEvent(
                    deviceId = event.deviceId,
                    eventTimeMs = event.eventTime,
                    source = event.source,
                    changedAxes = changed,
                ),
            )
        }
        return true
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId)
        if (device != null && ControllerDetection.isGameController(device.sources, device.isVirtual)) {
            Log.i(TAG, "Controller connected id=$deviceId name=\"${device.name}\"")
        }
        refreshDevices()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (sessions.containsKey(deviceId)) {
            Log.i(TAG, "Controller disconnected id=$deviceId")
        }
        refreshDevices()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        if (sessions.containsKey(deviceId)) {
            Log.i(TAG, "Controller changed id=$deviceId, re-reading InputDevice")
        }
        refreshDevices()
    }

    private fun sessionFor(deviceId: Int): DeviceSession? {
        sessions[deviceId]?.let { return it }
        if (deviceId in nonControllerIds) return null
        // Event from a device we have not enumerated yet (listener race); re-enumerate once.
        refreshDevices()
        return sessions[deviceId]
    }

    /** Re-read every InputDevice. InputDevice objects are snapshots and go stale on change. */
    private fun refreshDevices() {
        val all = inputManager.inputDeviceIds.toList().mapNotNull { id -> InputDevice.getDevice(id) }
        val infos = all.map(::readInfo)
        val (controllerInfos, others) = infos.partition { it.isGameController }

        val currentIds = controllerInfos.map { it.id }.toSet()
        sessions.keys.retainAll(currentIds)
        for (info in controllerInfos) {
            val existing = sessions[info.id]
            if (existing == null) {
                val session = DeviceSession(info)
                sessions[info.id] = session
                logDevice(info, session.layout)
            } else if (existing.info != info) {
                existing.info = info
                existing.layout = AxisLayout.resolve(info.joystickRanges)
                existing.update(existing.raw)
                logDevice(info, existing.layout)
            }
        }

        controllers = controllerInfos.sortedBy { it.id }
        otherDevices = others.sortedBy { it.id }
        nonControllerIds = others.map { it.id }.toSet()

        val current = selected
        if (current == null || current.info.id !in currentIds) {
            val next = controllers.firstOrNull { it.descriptor == lastSelectedDescriptor }
                ?: controllers.firstOrNull()
            if (next != null) {
                select(next.id)
            } else if (current != null) {
                selected = null
                Log.i(TAG, "No controller selected")
            }
        }
    }

    private fun appendRaw(event: RawEvent) {
        rawLog = RawEventLog.append(rawLog, event)
    }

    private fun logDevice(info: ControllerInfo, layout: AxisLayout) {
        Log.i(
            TAG,
            "Device id=${info.id} name=\"${info.name}\" vid=${ControllerDetection.formatUsbId(info.vendorId)} " +
                "pid=${ControllerDetection.formatUsbId(info.productId)} " +
                "sources=${ControllerDetection.formatSources(info.sources)} controllerNumber=${info.controllerNumber}",
        )
        for (r in info.motionRanges) {
            Log.i(
                TAG,
                "  range ${MotionEvent.axisToString(r.axis)} source=${Format.hex32(r.source)} " +
                    "min=${r.min} max=${r.max} flat=${r.flat} fuzz=${r.fuzz} resolution=${r.resolution}",
            )
        }
        for (note in layout.notes) Log.w(TAG, "Mapping: device ${info.id}: ${describe(note)}")
    }

    companion object {
        private const val TAG = "GameProbe"

        /** System keys the probe never swallows, even when a controller sends them. */
        private val PASS_THROUGH_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_HOME,
        )

        /** Keycodes checked with `InputDevice.hasKeys` to show what the key layout declares. */
        val PROBED_KEYS = intArrayOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_C,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Z,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        )

        fun readInfo(device: InputDevice): ControllerInfo {
            val declared = device.hasKeys(*PROBED_KEYS)
            return ControllerInfo(
                id = device.id,
                name = device.name ?: "",
                descriptor = device.descriptor ?: "",
                vendorId = device.vendorId,
                productId = device.productId,
                sources = device.sources,
                keyboardType = device.keyboardType,
                controllerNumber = device.controllerNumber,
                isExternal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) device.isExternal else null,
                isVirtual = device.isVirtual,
                hasVibrator = hasVibrator(device),
                hasBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) device.batteryState.isPresent else null,
                motionRanges = device.motionRanges.map {
                    AxisRange(it.axis, it.source, it.min, it.max, it.flat, it.fuzz, it.resolution)
                },
                declaredKeys = PROBED_KEYS.filterIndexed { index, _ -> declared[index] },
            )
        }

        private fun hasVibrator(device: InputDevice): Boolean? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                device.vibratorManager.vibratorIds.isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                device.vibrator.hasVibrator()
            }
        }.getOrNull()

        fun describe(note: LayoutNote): String = when (note) {
            LayoutNote.NoLeftStick -> "No AXIS_X/AXIS_Y reported; left stick unavailable."
            LayoutNote.NoRightStick -> "No AXIS_Z/AXIS_RZ or AXIS_RX/AXIS_RY reported; right stick unavailable."
            LayoutNote.RightStickOnRxRy -> "AXIS_Z/AXIS_RZ absent; right stick read from AXIS_RX/AXIS_RY."
            LayoutNote.NoAnalogTriggers ->
                "No analog trigger axis (LTRIGGER/RTRIGGER/BRAKE/GAS). Triggers may be digital keys only."
            is LayoutNote.UnmappedAxes ->
                "Axes not mapped to a normalized control: " +
                    note.axes.joinToString { MotionEvent.axisToString(it) } + "."
        }
    }
}
