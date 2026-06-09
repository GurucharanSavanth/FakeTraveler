#!/usr/bin/env bash
# drive.sh — launch + drive FakeTraveler on an Android emulator over adb.
#
# FakeTraveler is a GPS-mocking Android app (Java, F-Droid). There is no
# headless surface: you boot an emulator, install the debug APK, mark the app
# as the system "mock location app" (appops), then drive its Material UI by
# resolving resource-ids -> on-screen bounds via `uiautomator dump` and tapping.
#
# Every subcommand here was exercised by hand before being committed. Run with
# no args for usage. Screenshots land in $SHOTS (default /tmp/ft-shots).
#
# Requires: a working Android SDK (ANDROID_HOME), KVM for accel, and an AVD.
set -uo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMU="$ANDROID_HOME/emulator/emulator"
AVD="${AVD:-Pixel_10_Pro_XL}"
PKG="cl.coders.faketraveler"
ACT="$PKG/.MainActivity"
SHOTS="${SHOTS:-/tmp/ft-shots}"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"
mkdir -p "$SHOTS"

die() { echo "ERR: $*" >&2; exit 1; }

# Resolve a resource-id (short, e.g. editTextLat) to "centerX centerY".
# Dumps the live view hierarchy and averages the element's bounds rect.
_center() {
  local id="$1"
  "$ADB" shell uiautomator dump /sdcard/win.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/win.xml "$SHOTS/win.xml" >/dev/null 2>&1
  python3 - "$id" "$SHOTS/win.xml" <<'PY'
import re,sys
sid,path=sys.argv[1],sys.argv[2]
xml=open(path,encoding="utf-8").read()
# find the node carrying this resource-id, then its bounds rect
m=re.search(r'resource-id="cl\.coders\.faketraveler:id/'+re.escape(sid)+r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',xml)
if not m: sys.exit("no-bounds")
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
PY
}

# Tap an element by resource-id.
tapid() {
  local c; c=$(_center "$1") || die "id '$1' not on screen"
  "$ADB" shell input tap $c
}

# Replace the text of an EditText: focus, clear (MOVE_END + DELs), type.
typeid() {
  local id="$1" txt="$2" c
  c=$(_center "$id") || die "id '$id' not on screen"
  "$ADB" shell input tap $c
  "$ADB" shell input keyevent KEYCODE_MOVE_END
  for _ in $(seq 1 16); do "$ADB" shell input keyevent KEYCODE_DEL; done
  "$ADB" shell input text "$txt"
}

# Tap an element by visible text (for the Apply/Stop toggle button).
taptext() {
  local want="$1" c
  "$ADB" shell uiautomator dump /sdcard/win.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/win.xml "$SHOTS/win.xml" >/dev/null 2>&1
  c=$(python3 - "$want" "$SHOTS/win.xml" <<'PY'
import re,sys
want,path=sys.argv[1],sys.argv[2]
xml=open(path,encoding="utf-8").read()
m=re.search(r'text="'+re.escape(want)+r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',xml)
if not m: sys.exit("no-text")
x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2)
PY
) || die "text '$want' not on screen"
  "$ADB" shell input tap $c
}

ss() {
  local name="${1:-shot}"
  "$ADB" exec-out screencap -p > "$SHOTS/$name.png"
  echo "$SHOTS/$name.png"
}

# The cooldown module (P6-P8 FeatureFlag) blocks re-applying a mock for ~60min
# after a Stop. It pops an AlertDialog with a consent Switch (cooldown_ack) that
# must be ON before the override Button (cooldown_override) enables. If that
# dialog is up, ack + override; otherwise no-op. Returns 0 either way.
cooldown_override() {
  "$ADB" shell uiautomator dump /sdcard/win.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/win.xml "$SHOTS/win.xml" >/dev/null 2>&1
  grep -q 'id/cooldown_override' "$SHOTS/win.xml" || return 0
  echo "cooldown dialog present -> ack + override"
  tapid cooldown_ack
  sleep 1
  tapid cooldown_override
  sleep 1
}

boot() {
  if "$ADB" shell true >/dev/null 2>&1; then echo "device already up"; return 0; fi
  echo "booting $AVD ..."
  nohup "$EMU" -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -no-snapshot -gpu swiftshader_indirect -accel on >/tmp/ft-emu.log 2>&1 &
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2; done
  echo "boot_completed=1"
}

setup() {
  [ -f "$APK" ] || die "APK missing: $APK  (run: ./gradlew assembleDebug)"
  "$ADB" install -r -g "$APK" >/dev/null || die "install failed"
  # System gate: app must be the chosen mock-location provider, else
  # addTestProvider throws SecurityException. This is the one non-obvious step.
  "$ADB" shell appops set "$PKG" android:mock_location allow
  "$ADB" shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
  "$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
  echo "installed + mock_location granted"
}

launch() { "$ADB" shell am start -n "$ACT" >/dev/null; echo "launched $ACT"; }

# Print the mock location the app is pushing to each provider. The ground-truth
# signal is `dumpsys location` lines tagged `... mock]` for gps/network/fused.
mockstatus() {
  echo "--- providers' last location (mock = our spoof) ---"
  "$ADB" shell dumpsys location 2>/dev/null \
    | grep -E 'last location=Location\[(gps|network|fused)' | grep 'mock]'
}

# End-to-end smoke: fresh coords -> apply -> screenshot -> assert mock pushed.
verify() {
  local lat="${1:--33.4489}" lng="${2:--70.6693}"  # Santiago, CL
  # If currently mocking, stop first so the button reads "Apply".
  if "$ADB" shell uiautomator dump /sdcard/win.xml >/dev/null 2>&1 \
     && "$ADB" pull /sdcard/win.xml "$SHOTS/win.xml" >/dev/null 2>&1 \
     && grep -q 'text="Stop mocking"' "$SHOTS/win.xml"; then
    taptext "Stop mocking"; sleep 1
  fi
  typeid editTextLat "$lat"
  typeid editTextLng "$lng"
  "$ADB" shell input keyevent KEYCODE_BACK   # dismiss keyboard
  tapid button_applyStop
  sleep 1
  cooldown_override                          # clears the ~60min re-apply gate
  sleep 2
  ss verify
  echo "--- asserting gps provider carries mock ($lat,$lng) ---"
  # dumpsys formats coords to 6 decimals (e.g. 35.676200,139.650300) — match that.
  local want; want="$(printf '%.6f,%.6f' "$lat" "$lng")"
  # `--` guards $want starting with '-' (negative lat) from being read as a flag.
  if "$ADB" shell dumpsys location 2>/dev/null \
       | grep -E 'last location=Location\[gps' | grep 'mock]' | grep -qF -- "$want"; then
    echo "PASS: gps provider reports mock $want"
  else
    echo "FAIL: $want not on gps provider (see $SHOTS/verify.png)"; return 1
  fi
}

quit() { "$ADB" emu kill 2>/dev/null; echo "emulator killed"; }

cmd="${1:-help}"; shift || true
case "$cmd" in
  boot)       boot ;;
  setup)      setup ;;
  launch)     launch ;;
  ss)         ss "$@" ;;
  tapid)      tapid "$@" ;;
  typeid)     typeid "$@" ;;
  taptext)    taptext "$@" ;;
  coords)     typeid editTextLat "${1:?lat}"; typeid editTextLng "${2:?lng}"; \
              "$ADB" shell input keyevent KEYCODE_BACK; tapid button_applyStop; \
              sleep 1; cooldown_override; ss coords ;;
  mockstatus) mockstatus ;;
  verify)     verify "$@" ;;
  all)        boot; setup; launch; sleep 3; verify "$@" ;;
  quit)       quit ;;
  *) cat >&2 <<EOF
drive.sh — FakeTraveler emulator driver
  boot                 start headless emulator (\$AVD=$AVD), wait for boot
  setup                install debug APK + grant mock_location appop
  launch               start MainActivity
  coords <lat> <lng>   fill manual coords + tap Apply, screenshot
  verify [lat] [lng]   full smoke: stop->coords->apply->screenshot->assert
  ss [name]            screenshot to $SHOTS/<name>.png
  tapid <id>           tap element by resource-id
  typeid <id> <text>   replace EditText text by resource-id
  taptext <text>       tap element by visible text
  mockstatus           dump GPS provider's pushed location
  all [lat] [lng]      boot + setup + launch + verify
  quit                 kill emulator
EOF
  exit 1 ;;
esac
