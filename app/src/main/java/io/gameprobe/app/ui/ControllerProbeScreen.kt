package io.gameprobe.app.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.gameprobe.app.controller.AxisLayout
import io.gameprobe.app.controller.Button
import io.gameprobe.app.controller.ControllerDetection
import io.gameprobe.app.controller.ControllerInfo
import io.gameprobe.app.controller.ControllerProbe
import io.gameprobe.app.controller.DeviceSession
import io.gameprobe.app.controller.DpadSource
import io.gameprobe.app.controller.Format
import io.gameprobe.app.controller.RawEvent
import io.gameprobe.app.controller.RawKeyEvent
import io.gameprobe.app.controller.RawMotionEvent
import io.gameprobe.app.ui.components.ButtonIndicator
import io.gameprobe.app.ui.components.InfoRow
import io.gameprobe.app.ui.components.MonoText
import io.gameprobe.app.ui.components.Section
import io.gameprobe.app.ui.components.StickView
import io.gameprobe.app.ui.components.TriggerBar

/*
 * Each section reads only the snapshot state it displays, so high-frequency analog updates
 * recompose the live controls and axis table without rebuilding the rest of the screen.
 */

@Composable
fun ControllerProbeScreen(probe: ControllerProbe) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GameProbe", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Controller Probe",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val controllers = probe.controllers
            val session = probe.selected
            if (controllers.isEmpty() || session == null) {
                NoController()
            } else {
                ControllerSelector(controllers, session.info.id, probe::select)
                ControllerInfoSection(session)
                LiveControlsSection(session)
                MappingSection(session)
                AxesSection(session)
            }
            RawInputSection(probe)
            OtherDevicesSection(probe)
        }
    }
}

@Composable
private fun NoController() {
    Section("No controller detected") {
        Text("Connect a Bluetooth or USB controller\nand press any button.")
    }
}

@Composable
private fun ControllerSelector(controllers: List<ControllerInfo>, selectedId: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Detected controller${if (controllers.size > 1) "s (${controllers.size})" else ""}",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (info in controllers) {
                FilterChip(
                    selected = info.id == selectedId,
                    onClick = { onSelect(info.id) },
                    label = { Text("${info.name.ifBlank { "Unnamed" }} (#${info.id})") },
                )
            }
        }
    }
}

@Composable
private fun ControllerInfoSection(session: DeviceSession) {
    val info = session.info
    Section("Controller Info") {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.name.ifBlank { ControllerDetection.UNKNOWN }, style = MaterialTheme.typography.titleSmall)
                InfoRow("Android device ID", info.id.toString())
                InfoRow("VID", ControllerDetection.formatUsbId(info.vendorId))
                InfoRow("PID", ControllerDetection.formatUsbId(info.productId))
                InfoRow("Descriptor", info.descriptor.ifBlank { ControllerDetection.UNKNOWN })
                InfoRow("Sources", ControllerDetection.formatSources(info.sources))
                InfoRow("Controller number", if (info.controllerNumber == 0) "0 (not assigned)" else info.controllerNumber.toString())
                InfoRow("Keyboard type", ControllerDetection.keyboardTypeName(info.keyboardType))
                InfoRow("External", info.isExternal.orUnknown())
                InfoRow("Vibrator", info.hasVibrator.orUnknown())
                InfoRow("Battery", info.hasBattery.orUnknown())
                InfoRow("Connection", "${info.connectionType} (not exposed by Android)")
                InfoRow("Input mode", info.inputMode ?: "${ControllerDetection.UNKNOWN} (not exposed by Android)")
                InfoRow(
                    "Declared keys",
                    info.declaredKeys.joinToString(" ") { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }
                        .ifBlank { "none" },
                )
            }
        }
        Text(
            "Declared keys come from InputDevice.hasKeys (the key layout), not from pressing buttons.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Boolean?.orUnknown(): String = when (this) {
    true -> "yes"
    false -> "no"
    null -> ControllerDetection.UNKNOWN
}

@Composable
private fun LiveControlsSection(session: DeviceSession) {
    val state = session.state
    Section("Live Controls") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ButtonIndicator("L1", state.isPressed(Button.L1))
            ButtonIndicator("R1", state.isPressed(Button.R1))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TriggerBar("LT", "L2", state.leftTrigger, Modifier.weight(1f))
            TriggerBar("RT", "R2", state.rightTrigger, Modifier.weight(1f))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Diamond(
                    north = { ButtonIndicator("▲", state.isPressed(Button.DPAD_UP)) },
                    west = { ButtonIndicator("◀", state.isPressed(Button.DPAD_LEFT)) },
                    east = { ButtonIndicator("▶", state.isPressed(Button.DPAD_RIGHT)) },
                    south = { ButtonIndicator("▼", state.isPressed(Button.DPAD_DOWN)) },
                )
                val via = when {
                    state.dpadSources.isEmpty() -> "idle"
                    else -> state.dpadSources.joinToString(" + ") { if (it == DpadSource.KEYS) "keys" else "HAT" }
                }
                Caption("D-pad: $via")
                if (session.layout.hatX != null || session.layout.hatY != null) {
                    MonoText("HAT X${Format.axis(state.hatX)} Y${Format.axis(state.hatY)}")
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Diamond(
                    north = { ButtonIndicator("Y", state.isPressed(Button.FACE_NORTH)) },
                    west = { ButtonIndicator("X", state.isPressed(Button.FACE_WEST)) },
                    east = { ButtonIndicator("B", state.isPressed(Button.FACE_EAST)) },
                    south = { ButtonIndicator("A", state.isPressed(Button.FACE_SOUTH)) },
                )
                Caption("Face (Android keycodes)")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            LabeledIndicator("Select", state.isPressed(Button.SELECT))
            LabeledIndicator("Home", state.isPressed(Button.HOME))
            LabeledIndicator("Start", state.isPressed(Button.START))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StickView("Left Stick", state.leftStick, "L3", state.isPressed(Button.L3))
            StickView("Right Stick", state.rightStick, "R3", state.isPressed(Button.R3))
        }

        if (state.unmappedKeys.isNotEmpty()) {
            MonoText("Held, unmapped: " + state.unmappedKeys.sorted().joinToString { KeyEvent.keyCodeToString(it) })
        }
        Caption(
            "Face buttons are shown by Android keycode (A = bottom by convention). " +
                "The printed label on your controller may differ. " +
                "Home only appears if Android delivers it to apps.",
        )
    }
}

@Composable
private fun Diamond(
    north: @Composable () -> Unit,
    west: @Composable () -> Unit,
    east: @Composable () -> Unit,
    south: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        north()
        Row(verticalAlignment = Alignment.CenterVertically) {
            west()
            Spacer(Modifier.size(44.dp))
            east()
        }
        south()
    }
}

@Composable
private fun LabeledIndicator(label: String, pressed: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ButtonIndicator(label.take(3), pressed, size = 40.dp)
        Caption(label)
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun axisName(axis: Int?): String = axis?.let { MotionEvent.axisToString(it) } ?: "none"

@Composable
private fun MappingSection(session: DeviceSession) {
    val layout: AxisLayout = session.layout
    val unmappedSeen = session.unmappedKeysSeen
    Section("Mapping") {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoRow("Left stick", "${axisName(layout.leftX?.axis)} / ${axisName(layout.leftY?.axis)}")
                InfoRow("Right stick", "${axisName(layout.rightX?.axis)} / ${axisName(layout.rightY?.axis)}")
                InfoRow("LT axes", layout.leftTrigger.joinToString { axisName(it.axis) }.ifBlank { "none" })
                InfoRow("RT axes", layout.rightTrigger.joinToString { axisName(it.axis) }.ifBlank { "none" })
                InfoRow("D-pad HAT", "${axisName(layout.hatX?.axis)} / ${axisName(layout.hatY?.axis)}")
                for (note in layout.notes) MonoText("• " + ControllerProbe.describe(note))
                if (unmappedSeen.isNotEmpty()) {
                    MonoText("• Keys seen without a normalized control: " + unmappedSeen.sorted().joinToString { KeyEvent.keyCodeToString(it) })
                }
            }
        }
        Caption(
            "Stick dead zone: values with |raw| ≤ MotionRange.flat read as 0, others pass through. " +
                "When several trigger axes exist, the largest value is shown.",
        )
    }
}

@Composable
private fun AxesSection(session: DeviceSession) {
    val info = session.info
    val axes = session.raw.axes
    Section("Motion Ranges") {
        if (info.motionRanges.isEmpty()) {
            Text("This device reports no motion ranges.")
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (range in info.motionRanges) {
                    val isJoystick = ControllerDetection.hasSource(range.source, InputDevice.SOURCE_JOYSTICK)
                    val value = axes[range.axis]
                    Column {
                        MonoText(
                            MotionEvent.axisToString(range.axis).padEnd(16) +
                                (if (isJoystick) value?.let { Format.axis(it) } ?: "  (no event yet)" else ""),
                        )
                        MonoText(
                            "  min ${Format.plain(range.min)} max ${Format.plain(range.max)} " +
                                "flat ${Format.plain(range.flat)} fuzz ${Format.plain(range.fuzz)} " +
                                "res ${Format.plain(range.resolution)}",
                        )
                        MonoText("  source ${ControllerDetection.formatSources(range.source)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun RawInputSection(probe: ControllerProbe) {
    val log = probe.rawLog
    Section(
        "Raw Input",
        action = { TextButton(onClick = probe::clearRawLog, enabled = log.isNotEmpty()) { Text("Clear") } },
    ) {
        if (log.isEmpty()) {
            Caption("Key and joystick events from controllers appear here, newest first.")
        } else {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (event in log.asReversed()) RawEventRow(event)
                }
            }
        }
    }
}

@Composable
private fun RawEventRow(event: RawEvent) {
    val time = Format.seconds(event.eventTimeMs)
    when (event) {
        is RawKeyEvent -> MonoText(
            "$time  KeyEvent ${if (event.down) "DOWN" else "UP"}\n" +
                "  ${KeyEvent.keyCodeToString(event.keyCode)}\n" +
                "  deviceId=${event.deviceId} scanCode=${event.scanCode} source=${Format.hex32(event.source)}",
        )
        is RawMotionEvent -> MonoText(
            "$time  MotionEvent" + (if (event.samples > 1) " ×${event.samples}" else "") + "\n" +
                "  deviceId=${event.deviceId} source=${Format.hex32(event.source)}\n" +
                event.changedAxes.entries.sortedBy { it.key }.joinToString("\n") { (axis, value) ->
                    "  ${MotionEvent.axisToString(axis)}=${Format.axis(value).trim()}"
                },
        )
    }
}

@Composable
private fun OtherDevicesSection(probe: ControllerProbe) {
    val others = probe.otherDevices
    var expanded by rememberSaveable { mutableStateOf(false) }
    Section(
        "Other input devices (${others.size})",
        action = { TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") } },
    ) {
        Caption("Not treated as controllers: no GAMEPAD or JOYSTICK source, or virtual.")
        if (expanded) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (info in others) {
                        MonoText(
                            "#${info.id} ${info.name}\n  ${ControllerDetection.formatSources(info.sources)}" +
                                (if (info.isVirtual) " virtual" else ""),
                        )
                    }
                }
            }
        }
    }
}
