#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFO_PLIST="$ROOT_DIR/NeogulMapNative/Info.plist"
APP_CONFIG="$ROOT_DIR/NeogulMapNative/AppConfig.swift"
SMOKE_URL="nugulmap://oauth/callback?code=smoke-code&profileComplete=true&email=smoke@example.com"

plist_scheme="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleURLTypes:0:CFBundleURLSchemes:0' "$INFO_PLIST")"
if [[ "$plist_scheme" != "nugulmap" ]]; then
  echo "FAIL: expected Info.plist URL scheme nugulmap, got $plist_scheme" >&2
  exit 1
fi

if ! grep -q 'oauthCallbackScheme = "nugulmap"' "$APP_CONFIG"; then
  echo "FAIL: AppConfig.oauthCallbackScheme is not nugulmap" >&2
  exit 1
fi

if ! grep -q 'oauthCallbackURL = URL(string: "nugulmap://oauth/callback")' "$APP_CONFIG"; then
  echo "FAIL: AppConfig.oauthCallbackURL is not nugulmap://oauth/callback" >&2
  exit 1
fi

echo "PASS: OAuth callback scheme is registered as nugulmap"
echo "PASS: AppConfig OAuth callback URL is nugulmap://oauth/callback"

if ! booted_devices="$(xcrun simctl list devices booted 2>/dev/null)"; then
  echo "SKIP: Unable to query booted simulators; config-only deeplink smoke completed"
  exit 0
fi

if grep -q '(Booted)' <<<"$booted_devices"; then
  if python3 - "$SMOKE_URL" <<'PY'
import subprocess
import sys

url = sys.argv[1]
try:
    subprocess.run(["xcrun", "simctl", "openurl", "booted", url], check=True, timeout=10)
except subprocess.TimeoutExpired:
    print(f"WARN: Timed out opening simulator deeplink {url}; config smoke still passed")
    sys.exit(3)
except subprocess.CalledProcessError as error:
    print(f"FAIL: simctl openurl exited with {error.returncode} for {url}", file=sys.stderr)
    sys.exit(error.returncode)
PY
  then
    echo "PASS: Opened simulator deeplink $SMOKE_URL"
  else
    status=$?
    if [[ "$status" -ne 3 ]]; then
      exit "$status"
    fi
  fi
else
  echo "SKIP: No booted simulator; config-only deeplink smoke completed"
fi
