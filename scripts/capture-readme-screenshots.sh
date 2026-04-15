#!/usr/bin/env bash
# Capture README screenshots from a running emulator or USB device.
# Prerequisites: adb in PATH, DeckBridge installed (./gradlew :app:installDebug).
set -euo pipefail
PKG="com.example.deckbridge"
ACTIVITY="${PKG}/.MainActivity"
OUT="$(cd "$(dirname "$0")/.." && pwd)/docs/readme/screenshots"
mkdir -p "$OUT"

if ! adb devices | awk 'NR>1 && $2=="device"{found=1} END{exit !found}'; then
  echo "No adb device in 'device' state. Start an emulator or plug a phone with USB debugging." >&2
  exit 1
fi

# Parse "Physical size: WxH" (fallback: last WxH token on the line)
res_line=$(adb shell wm size | tr -d '\r')
res=$(echo "$res_line" | grep -Eo '[0-9]+x[0-9]+' | tail -1)
if [[ -z "$res" ]]; then
  echo "Could not parse screen size from: $res_line" >&2
  exit 1
fi
W=${res%x*}
H=${res#*x}
echo "Detected display: ${W}x${H}"

# Tap targets scale with resolution (portrait). Calibrated from UIAutomator on 720x1600:
# Skip / Omitir ~ center (651,117); "Continue without PC" ~ lower center; Settings gear ~ top-right.
SKIP_X=$((W * 651 / 720))
SKIP_Y=$((H * 117 / 1600))
CONT_X=$((W / 2))
CONT_Y=$((H * 90 / 100))
SET_X=$((W * 678 / 720))
SET_Y=$((H * 125 / 1600))

echo "Clearing app data for a clean first-run flow..."
adb shell pm clear "$PKG" || true
sleep 1

echo "Launching ${ACTIVITY}..."
adb shell am start -W -n "$ACTIVITY"
# Cold start: repository + onboarding gate can take several seconds on device.
sleep 14

echo "-> $OUT/01-onboarding.png"
adb exec-out screencap -p > "$OUT/01-onboarding.png"

echo "Tapping Skip at ($SKIP_X,$SKIP_Y)..."
adb shell input tap "$SKIP_X" "$SKIP_Y"
sleep 3

echo "-> $OUT/02-after-onboarding.png"
adb exec-out screencap -p > "$OUT/02-after-onboarding.png"

echo "Attempting 'Continue without PC link' at ($CONT_X,$CONT_Y)..."
adb shell input tap "$CONT_X" "$CONT_Y"
sleep 3

echo "-> $OUT/03-dashboard.png"
adb exec-out screencap -p > "$OUT/03-dashboard.png"

echo "Opening Settings at ($SET_X,$SET_Y)..."
adb shell input tap "$SET_X" "$SET_Y"
sleep 3

echo "-> $OUT/04-settings.png"
adb exec-out screencap -p > "$OUT/04-settings.png"

echo "Done. Review images in $OUT"
