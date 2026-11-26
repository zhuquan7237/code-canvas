#!/usr/bin/env bash
set -euo pipefail
: "${SIM_ID:?Set SIM_ID to an iOS 27 simulator UDID}"
root="$(cd "$(dirname "$0")" && pwd)"
tmp="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
output="$tmp/lab-evidence"
derived="$tmp/lab-build"
bundle=com.nous.codecanvas.designlab
mkdir -p "$output"

if [ ! -d "$derived/Build/Products/Debug-iphonesimulator/DesignLab.app" ]; then
  xcodebuild build -project "$root/DesignLab.xcodeproj" -scheme DesignLab \
    -destination "generic/platform=iOS Simulator" -derivedDataPath "$derived"
fi
echo "Booting simulator $SIM_ID"
perl -e 'alarm 150; exec @ARGV or die $!' xcrun simctl bootstatus "$SIM_ID" -b
xcrun simctl status_bar "$SIM_ID" override --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 --batteryState discharging --batteryLevel 100 || echo "Status-bar override unavailable; continuing with actual device status"
xcrun simctl install "$SIM_ID" "$derived/Build/Products/Debug-iphonesimulator/DesignLab.app"

capture() {
  xcrun simctl terminate "$SIM_ID" "$bundle" 2>/dev/null || true
  SIMCTL_CHILD_DESIGN_LAB_OPEN_STUDY="$2" xcrun simctl launch "$SIM_ID" "$bundle"
  sleep 4
  xcrun simctl io "$SIM_ID" screenshot "$output/$1.png"
}
xcrun simctl ui "$SIM_ID" appearance light
capture 01-library-light 0
capture 02-study-light 1
xcrun simctl ui "$SIM_ID" appearance dark
capture 03-study-dark 1
xcrun simctl ui "$SIM_ID" appearance light
xcrun simctl ui "$SIM_ID" content_size accessibility-extra-extra-extra-large
capture 04-library-large-text 0
capture 05-study-large-text 1
xcrun simctl ui "$SIM_ID" content_size large
xcrun simctl terminate "$SIM_ID" "$bundle"
