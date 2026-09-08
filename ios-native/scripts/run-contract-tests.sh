#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IOS_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
TEST_ROOT=${NUGUL_IOS_TEST_ROOT:-$(mktemp -d "${TMPDIR:-/tmp}/nugulmap-ios-tests.XXXXXX")}
REMOVE_TEST_ROOT=false

if [ -z "${NUGUL_IOS_TEST_ROOT:-}" ]; then
  REMOVE_TEST_ROOT=true
fi

cleanup() {
  if [ "$REMOVE_TEST_ROOT" = true ]; then
    rm -rf "$TEST_ROOT"
  fi
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$TEST_ROOT"

compile_and_run() {
  name=$1
  shift
  xcrun swiftc -parse-as-library "$@" -o "$TEST_ROOT/$name"
  "$TEST_ROOT/$name"
}

API_CLIENT_SOURCES="
$IOS_ROOT/NeogulMapNative/AppConfig.swift
$IOS_ROOT/NeogulMapNative/Models/SmokingZone.swift
$IOS_ROOT/NeogulMapNative/Models/ZoneReview.swift
$IOS_ROOT/NeogulMapNative/Models/LocationLetter.swift
$IOS_ROOT/NeogulMapNative/Services/NugulAPIClient.swift
"

for test_name in \
  AccountDeletionContractTests \
  AuthRefreshContractTests \
  ImageURLContractTests \
  MapInsightContractTests \
  OAuthRecoveryContractTests \
  RecommendationAPIContractTests \
  ZoneReportContractTests
do
  # Word splitting is intentional: every source path in this repository is space-free.
  # shellcheck disable=SC2086
  compile_and_run "$test_name" $API_CLIENT_SOURCES "$IOS_ROOT/Tests/$test_name.swift"
done

compile_and_run \
  Season3InsightPresentationTests \
  "$IOS_ROOT/NeogulMapNative/Models/SmokingZone.swift" \
  "$IOS_ROOT/Tests/Season3InsightPresentationTests.swift"

compile_and_run \
  FixtureZoneContractTests \
  "$IOS_ROOT/NeogulMapNative/Models/SmokingZone.swift" \
  "$IOS_ROOT/Tests/FixtureZoneContractTests.swift"

# Word splitting is intentional: every source path in this repository is space-free.
# shellcheck disable=SC2086
compile_and_run \
  SmokingReleaseNetworkContractTests \
  $API_CLIENT_SOURCES \
  "$IOS_ROOT/NeogulMapNative/ViewModels/ZoneExplorerModel.swift" \
  "$IOS_ROOT/Tests/SmokingReleaseNetworkContractTests.swift"

# Word splitting is intentional: every source path in this repository is space-free.
# shellcheck disable=SC2086
compile_and_run \
  RecommendationRaceContractTests \
  $API_CLIENT_SOURCES \
  "$IOS_ROOT/NeogulMapNative/ViewModels/ZoneExplorerModel.swift" \
  "$IOS_ROOT/Tests/RecommendationRaceContractTests.swift"

compile_and_run \
  MapSurfaceContractTests \
  "$IOS_ROOT/Tests/MapSurfaceContractTests.swift"

compile_and_run \
  MapDicePickerTests \
  "$IOS_ROOT/NeogulMapNative/Models/MapDicePicker.swift" \
  "$IOS_ROOT/Tests/MapDicePickerTests.swift"
