# GameProbe

GameProbe tests how Android game controllers are actually exposed to apps and games.

A controller can look fine to Android while a particular game still misses a trigger,
a menu button or the D-pad. Finding out where input gets lost starts with knowing exactly
what Android delivered. GameProbe records that evidence.

## Current status

**v0.1: Controller Probe.** An Android app that detects connected game controllers and
shows their input in real time, both as raw Android events and as a small normalized
control model.

Game compatibility reports are planned but not part of v0.1. There is no backend, no
account and no network access.

## What v0.1 does

- Detects controllers by Android input source (`SOURCE_GAMEPAD` or `SOURCE_JOYSTICK`),
  not by device name. Touchscreens, keyboards, D-pad-only devices and virtual devices are
  excluded and listed separately under "Other input devices".
- Handles connect, disconnect and reconnect while the app is running, and lets you
  choose between multiple connected controllers.
- Shows `InputDevice` metadata: name, device ID, descriptor, vendor/product ID
  (`UNKNOWN` when Android reports 0), sources, controller number, keyboard type,
  external/vibrator/battery flags, and the gamepad keys the key layout declares.
- Lists every `MotionRange` with axis, min, max, flat, fuzz, resolution, source and the
  live value.
- Visualizes face buttons, L1/R1, analog triggers and digital L2/R2 keys, both sticks
  (raw and dead-zoned), D-pad from key events or HAT axes, L3/R3, Start, Select/Back and
  Home/Mode.
- Keeps a bounded raw input log of `KeyEvent`s (keycode, scan code, source, device) and
  joystick `MotionEvent`s (changed axes, merged sample count).
- Reports mapping notes, such as axes that are not mapped to a normalized control, a
  right stick on `AXIS_RX/RY` instead of `AXIS_Z/RZ`, or keycodes with no normalized
  button.

### How input is normalized

Mapping follows Android's documented controller profile and the device's own reported
motion ranges:

| Control | Source |
| --- | --- |
| Face buttons | `KEYCODE_BUTTON_A/B/X/Y` → south/east/west/north |
| Shoulders | `KEYCODE_BUTTON_L1/R1` |
| Triggers | `AXIS_LTRIGGER`/`AXIS_BRAKE`, `AXIS_RTRIGGER`/`AXIS_GAS` (largest wins), plus digital `KEYCODE_BUTTON_L2/R2` shown separately |
| Left stick | `AXIS_X/Y` |
| Right stick | `AXIS_Z/RZ`, or `AXIS_RX/RY` only if Z/RZ are absent |
| D-pad | `KEYCODE_DPAD_*` (including diagonals) or `AXIS_HAT_X/Y` |
| Stick clicks | `KEYCODE_BUTTON_THUMBL/THUMBR` |
| Start / Select / Home | `KEYCODE_BUTTON_START`, `KEYCODE_BUTTON_SELECT` or `KEYCODE_BACK`, `KEYCODE_BUTTON_MODE` |

Stick dead zones use each axis's `MotionRange.flat`: values with `|raw| <= flat` read as 0,
and other values pass through unchanged. Raw values are always shown next to normalized ones.
Face buttons are labeled by Android keycode, which may not match the printed label on the
controller.

## Build

Requirements: JDK 17 or newer and the Android SDK with platform 37 installed. Android
Studio Panda 4 (2025.3.4) or newer can open the project directly.

```sh
# Point Gradle at your SDK, either with ANDROID_HOME or a local.properties file:
# sdk.dir=/path/to/Android/Sdk

./gradlew assembleDebug        # Windows: .\gradlew.bat assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open GameProbe, connect a controller over Bluetooth or USB, and press any button.
While the probe is in the foreground it captures controller buttons (including B and
Back), so they don't navigate the app. Use the phone's own navigation to leave.

Filter logcat with `adb logcat -s GameProbe` to see connect/disconnect, motion ranges,
key events and mapping notes. Analog values are not logged per frame.

## Supported input categories

Face buttons, shoulder buttons, analog and digital triggers, both sticks, D-pad as keys
or HAT axes, stick clicks, Start, Select/Back, Home/Mode, and any other keycode or axis
the controller sends. Unmapped keycodes and axes appear in the raw views.

## Known limitations

- Hardware coverage: the input handling has not yet been verified on a physical
  controller. The app has been checked on an Android 16 emulator, which has no gamepad.
- Connection type (USB vs Bluetooth) and vendor controller mode (XInput, DInput, Switch
  and so on) are not exposed by public Android APIs, so both show `UNKNOWN`. The data
  model has fields reserved for them.
- The system usually handles Home/Guide buttons before apps see them. GameProbe only
  shows them if Android delivers them.
- Motion sensors, touchpads, vibration output, lights and battery level are not probed
  beyond the presence flags listed above.
- The normalized model follows Android's documented layout. Controllers that report
  sticks or triggers on other axes appear in the raw views and mapping notes rather than
  being remapped by guesswork.
- Requires Android 8.0 (API 26) or newer.

## Privacy

GameProbe declares no permissions of its own: no network, storage, location or Bluetooth
access. It collects no analytics. The only permission entry in the built manifest is an
app-private signature permission added by AndroidX.

## License

[MIT](LICENSE)
