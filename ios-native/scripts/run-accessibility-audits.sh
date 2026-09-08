#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_PATH="${IOS_ROOT}/NeogulMapNative.xcodeproj"
SCHEME="NeogulMapNative"
FIXTURE_ROOT="${IOS_ROOT}/UITests/Fixtures"
FIXTURE_SERVER="${IOS_ROOT}/UITests/fixture_server.py"
FIXTURE_PORT=18765
RESULT_ROOT="${TMPDIR:-/tmp}/nugulmap-ios-accessibility-audits-$(date +%Y%m%d-%H%M%S)"
IOS_A11Y_PHONE_NAME="${IOS_A11Y_PHONE_NAME:-iPhone 17 Pro Max}"
IOS_A11Y_TABLET_NAME="${IOS_A11Y_TABLET_NAME:-iPad Pro 13-inch (M5)}"
IOS_A11Y_PHONE_DESTINATION="${IOS_A11Y_PHONE_DESTINATION:-platform=iOS Simulator,name=iPhone 17 Pro Max,OS=latest}"
IOS_A11Y_TABLET_DESTINATION="${IOS_A11Y_TABLET_DESTINATION:-platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=latest}"
APP_BUNDLE_ID="com.nugulmap.native"

resolve_device_udid() {
  local device_name="$1"
  xcrun simctl list devices available --json | python3 -c '
import json
import sys

name = sys.argv[1]
devices = json.load(sys.stdin)["devices"]
matches = [device["udid"] for runtime in devices.values() for device in runtime if device.get("name") == name]
if not matches:
    raise SystemExit(f"No available simulator named {name}")
print(matches[-1])
' "${device_name}"
}

prepare_location_prompt() {
  local device_udid="$1"
  xcrun simctl boot "${device_udid}" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "${device_udid}" -b >/dev/null
  xcrun simctl privacy "${device_udid}" reset location "${APP_BUNDLE_ID}"
}

reboot_simulator() {
  local device_udid="$1"
  xcrun simctl shutdown "${device_udid}" >/dev/null 2>&1 || true
  xcrun simctl boot "${device_udid}" >/dev/null
  xcrun simctl bootstatus "${device_udid}" -b >/dev/null
}

mkdir -p "${RESULT_ROOT}"
python3 "${FIXTURE_SERVER}" --port "${FIXTURE_PORT}" --directory "${FIXTURE_ROOT}" \
  >"${RESULT_ROOT}/fixture-server.log" 2>&1 &
FIXTURE_PID=$!
trap 'kill "${FIXTURE_PID}" 2>/dev/null || true' EXIT

for _ in {1..30}; do
  if curl --fail --silent "http://127.0.0.1:${FIXTURE_PORT}/api/zones/bounds" >/dev/null; then
    break
  fi
  sleep 0.1
done
curl --fail --silent "http://127.0.0.1:${FIXTURE_PORT}/api/zones/bounds" >/dev/null

PHONE_UDID="${IOS_A11Y_PHONE_UDID:-$(resolve_device_udid "${IOS_A11Y_PHONE_NAME}")}"
TABLET_UDID="${IOS_A11Y_TABLET_UDID:-$(resolve_device_udid "${IOS_A11Y_TABLET_NAME}")}"

prepare_location_prompt "${PHONE_UDID}"

xcodebuild test \
  -project "${PROJECT_PATH}" \
  -scheme "${SCHEME}" \
  -destination "${IOS_A11Y_PHONE_DESTINATION}" \
  -only-testing:NeogulMapNativeUITests \
  -derivedDataPath "${RESULT_ROOT}/DerivedData-iPhone" \
  -resultBundlePath "${RESULT_ROOT}/iPhone.xcresult"

reboot_simulator "${TABLET_UDID}"
prepare_location_prompt "${TABLET_UDID}"

xcodebuild test \
  -project "${PROJECT_PATH}" \
  -scheme "${SCHEME}" \
  -destination "${IOS_A11Y_TABLET_DESTINATION}" \
  -only-testing:NeogulMapNativeUITests \
  -derivedDataPath "${RESULT_ROOT}/DerivedData-iPad" \
  -resultBundlePath "${RESULT_ROOT}/iPad.xcresult"

echo "Accessibility audit result bundles: ${RESULT_ROOT}"
