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

echo "Clearing app data for a clean first-run flow…"
adb shell pm clear "$PKG" || true
sleep 1

echo "Launching $ACTIVITY…"
adb shell am start -W -n "$ACTIVITY"
sleep 6

echo "→ $OUT/01-onboarding.png"
adb exec-out screencap -p > "$OUT/01-onboarding.png"

# Skip onboarding (top-right TextButton area, portrait ~1080 wide)
echo "Tapping Skip…"
adb shell input tap 1000 180
sleep 2

echo "→ $OUT/02-after-onboarding.png"
adb exec-out screencap -p > "$OUT/02-after-onboarding.png"

# If PC connection gate is shown, continue without link (center-bottom)
echo "Attempting 'Continue without PC link' if visible…"
adb shell input tap 540 2050
sleep 2

echo "→ $OUT/03-dashboard.png"
adb exec-out screencap -p > "$OUT/03-dashboard.png"

# Settings gear (top-right chrome)
echo "Opening Settings…"
adb shell input tap 1020 200
sleep 2

echo "→ $OUT/04-settings.png"
adb exec-out screencap -p > "$OUT/04-settings.png"

echo "Done. Review images in $OUT and adjust tap coordinates if your theme or DPI differs."
