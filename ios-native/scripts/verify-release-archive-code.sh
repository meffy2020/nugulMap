#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IOS_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$IOS_ROOT/.." && pwd)
ARCHIVE_ROOT=${NUGUL_IOS_ARCHIVE_ROOT:-$(mktemp -d "${TMPDIR:-/tmp}/nugulmap-ios-archive.XXXXXX")}
ARCHIVE_PATH="$ARCHIVE_ROOT/NeogulMapNative.xcarchive"
APP_PATH="$ARCHIVE_PATH/Products/Applications/NeogulMapNative.app"

xcodebuild -quiet \
  -project "$IOS_ROOT/NeogulMapNative.xcodeproj" \
  -scheme NeogulMapNative \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  CODE_SIGNING_ALLOWED=NO \
  archive

test -f "$APP_PATH/NeogulMapNative"
test -f "$APP_PATH/Info.plist"
test -f "$APP_PATH/PrivacyInfo.xcprivacy"
plutil -lint "$APP_PATH/Info.plist" "$APP_PATH/PrivacyInfo.xcprivacy" >/dev/null
plutil -extract UILaunchScreen json -o - "$APP_PATH/Info.plist" >/dev/null
lipo -archs "$APP_PATH/NeogulMapNative" | grep -q '\<arm64\>'
test "$(plutil -extract CFBundleIdentifier raw -o - "$APP_PATH/Info.plist")" = "com.nugulmap.native"

printf 'PASS: unsigned arm64 Release archive code, launch screen, and privacy manifest verified\n'
printf 'Archive: %s\n' "$ARCHIVE_PATH"
printf 'Project: %s\n' "$PROJECT_ROOT"
