---
name: run-faketraveler
description: Build, launch, drive, and screenshot the FakeTraveler Android GPS-mocking app on an emulator. Use when asked to run, start, install, screenshot, smoke-test, or verify FakeTraveler / cl.coders.faketraveler, or to confirm a mock-location change works on a real device (not just unit tests).
---

# Run FakeTraveler

FakeTraveler is a Java Android app (F-Droid) that mocks the system GPS. There
is **no headless surface** — you boot an Android emulator, install the debug
APK, mark the app as the OS "mock location app" (`appops`), then drive its
Material UI over `adb` by resolving `resource-id`s to on-screen bounds via
`uiautomator dump` and tapping. Ground truth that mocking works is **not** the
UI chip — it's `dumpsys location` showing your coords tagged `mock]` on the
gps/network/fused providers.

The driver is **`.claude/skills/run-faketraveler/drive.sh`** (bash + adb).
All paths below are relative to the repo root (`<unit>/`). Screenshots land in
`/tmp/ft-shots/`.

## Prerequisites

This dev box (CachyOS/Arch) already had the toolchain — **no package install
was needed**. What must exist:

- **JDK 21** (`java -version` → 21) — Gradle toolchain pins 21.
- **Android SDK** at `$ANDROID_HOME` (`~/Android/Sdk`) with `platform-tools`
  (adb), `emulator`, build-tools, and a system image. `adb`/`emulator` resolve
  from there.
- **An AVD** named `Pixel_10_Pro_XL` (override with `AVD=<name>`). It runs the
  `android-37` system image; the app (minSdk 21 / targetSdk 36) installs fine.
- **KVM** writable (`test -w /dev/kvm`) for accelerated boot.
- **python3** — the driver uses it to parse `uiautomator` XML bounds.

```bash
java -version 2>&1 | head -1          # openjdk 21.x
test -w /dev/kvm && echo kvm-ok       # accel available
"$ANDROID_HOME/emulator/emulator" -list-avds   # must list an AVD
```

## Build

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk  (~9.7 MB, BUILD SUCCESSFUL ~13s)
```

## Run (agent path) — the driver

One shot, from a cold machine to a verified mock:

```bash
.claude/skills/run-faketraveler/drive.sh all 51.5074 -0.1278
# boot emulator -> install+appops -> launch -> set coords -> apply
# -> clear cooldown dialog -> screenshot -> assert gps provider carries mock
```

Or step by step (each is idempotent):

```bash
D=.claude/skills/run-faketraveler/drive.sh
$D boot                       # start headless emulator, wait for boot_completed=1
$D setup                      # install -r -g APK + appops mock_location allow + grant perms
$D launch                     # am start MainActivity
$D verify 35.6762 139.6503    # stop->coords->apply->cooldown-override->assert  (Tokyo)
$D mockstatus                 # print providers' mock location (gps/network/fused)
$D ss mylabel                 # screenshot -> /tmp/ft-shots/mylabel.png
$D quit                       # adb emu kill
```

`verify`/`all` print `PASS: gps provider reports mock <lat>,<lng>` on success.
A passing run also leaves `/tmp/ft-shots/verify.png` showing "Mocking active",
the map recentered, and a "Mocked location applied." snackbar.

Lower-level UI pokes (id-addressed, bounds resolved live each call):

```bash
$D typeid editTextLat -33.4489     # replace an EditText's text by resource-id
$D tapid  button_applyStop         # tap an element by resource-id
$D taptext "Stop mocking"          # tap an element by visible text
```

## Run (human path)

Open the APK on a real phone, then **Developer options → Select mock location
app → FakeTraveler** (the GUI equivalent of the `appops` line), enter lat/lng,
tap **Apply mock location**. The emulator/adb path above is the only one that's
scriptable; the manual path can't be driven from here.

## Test — currently broken, use the driver instead

The JVM unit suite **does not compile** right now, so `./gradlew test` and
`./gradlew :app:testDebugUnitTest` both fail with **61 errors**. The test
sources were written against JUnit 5 (`import org.junit.jupiter.api.*`) but
`app/build.gradle` only declares JUnit 4 (`junit:junit:4.13.2`) — there is no
`junit-jupiter` dependency. Reproduce the diagnostic with:

```bash
./gradlew :app:compileDebugUnitTestJavaWithJavac 2>&1 | grep -m1 'jupiter'
# -> ...GeoUtilsTest.java:6: error: package org.junit.jupiter.api does not exist
```

Adding the jupiter dep would violate SPEC §C ("ZERO new external deps"), and
pinning minSdk/Material is also frozen there — so do **not** "fix" it casually.
Until it's reconciled, the emulator driver above (`dumpsys location` proof) is
the real verification path for any mock-location change.

## Gotchas (battle scars from this session)

- **`appops set <pkg> android:mock_location allow` is mandatory.** Without it,
  `addTestProvider` throws `SecurityException` and *nothing mocks* — the app
  shows a vague failure, not a permission prompt. `drive.sh setup` does it.
- **Cooldown module blocks re-apply for ~60 min after a Stop.** Tapping Apply
  pops a "Cooldown required 59:58" `AlertDialog`. The override `Button`
  (`cooldown_override`) stays **disabled** until you flip the consent `Switch`
  (`cooldown_ack`). `drive.sh` auto-handles this (`cooldown_override()`); if you
  drive by hand: `taptext "I understand the detection risk"` then
  `taptext "Override and apply now"`. This is a `FeatureFlag`-gated P6–P8 module.
- **State persists across launches.** `START_STICKY` + prefs-resume means a
  fresh `launch` often shows "Mocking active" from a *prior* session. `verify`
  stops first to get a clean baseline.
- **`dumpsys location` prints coords to 6 decimals** (`51.507400,-0.127800`).
  Assertions must format input with `%.6f` — matching `%.4f` silently FAILS
  even though the mock applied. (Hit this exact bug; fixed in the driver.)
- **Never hardcode tap coordinates.** Bounds shift with screen size, so the
  driver re-runs `uiautomator dump` and re-derives the center every tap/type.
- **EditTexts have no select-all over adb.** The driver clears via
  `KEYCODE_MOVE_END` + 16× `KEYCODE_DEL` before `input text`.
- **Headless GPU is fine.** `-gpu swiftshader_indirect` renders the Leaflet
  WebView map with no host display/GPU. `-no-window` is enough.
- **Negative latitudes break naive `grep`.** Asserting a southern-hemisphere
  coord (`-33.868800,...`) with `grep -F "$want"` makes grep read the leading
  `-` as a flag (`invalid option -- '.'`). The driver guards it with
  `grep -F -- "$want"`. (Hit this on the Sydney run; fixed.)
- **The real proof is three providers.** The app mocks gps **and** network
  **and** fused (fused on API ≥31), all tagged `mock]` in `dumpsys location` —
  the UI "Mocking active" chip is necessary but not sufficient.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `verify` says FAIL but `mockstatus` shows the coords | coord-precision mismatch — assert with `%.6f`, already fixed in driver. |
| Apply does nothing / "Cooldown required" dialog stuck | flip `cooldown_ack`, then `cooldown_override` (driver does this automatically). |
| `id '<x>' not on screen` | the element isn't visible (wrong screen/dialog up) — `ss` to look, or dismiss the modal first. |
| `SecurityException` in logcat on apply | mock_location appop not set — run `drive.sh setup` (or it was reset by a reinstall). |
| Emulator won't boot / no accel | `test -w /dev/kvm`; if not writable, add your user to the `kvm` group. |
| `./gradlew test` → "package org.junit.jupiter.api does not exist" (61 errors) | known repo state — tests are JUnit 5 but only JUnit 4 is on the test classpath. Not yours to fix here (adding jupiter breaks §C). Verify via the driver instead. |
