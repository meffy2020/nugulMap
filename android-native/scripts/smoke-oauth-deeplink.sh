#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="com.nugulmap.nativeapp"
SMOKE_URI="nugulmap://oauth/callback?code=smoke-code"
MERGED_MANIFEST="$ROOT_DIR/app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"

cd "$ROOT_DIR"

if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  fi
fi

./gradlew :app:processDebugMainManifest >/dev/null

python3 - "$MERGED_MANIFEST" <<'PY'
import sys
import xml.etree.ElementTree as ET

manifest_path = sys.argv[1]
android = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(manifest_path).getroot()
expected = {'scheme': 'nugulmap', 'host': 'oauth', 'path': '/callback'}
for data in root.findall('.//data'):
    actual = {key: data.get(android + key) for key in expected}
    if actual == expected:
        print(f"PASS manifest deeplink: {manifest_path} declares nugulmap://oauth/callback")
        break
else:
    raise SystemExit(f"FAIL manifest deeplink: missing {expected} in {manifest_path}")
PY

ADB_BIN="${ADB:-}"
if [[ -z "$ADB_BIN" ]]; then
  if command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
  elif [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
    ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"
  fi
fi

if [[ -n "$ADB_BIN" ]] && "$ADB_BIN" get-state >/dev/null 2>&1; then
  "$ADB_BIN" shell am start -W -a android.intent.action.VIEW -d "$SMOKE_URI" "$PACKAGE_NAME"
  echo "PASS adb deeplink smoke: launched $SMOKE_URI"
else
  echo "SKIP adb deeplink smoke: adb device unavailable; static manifest smoke passed"
fi
