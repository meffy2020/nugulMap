#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$API_ROOT/../.." && pwd)"

PORT="${PORT:-18080}"
MOCK_PORT="${MOCK_PORT:-18081}"
POPUP_TRENDS_FILE="${POPUP_TRENDS_FILE:-$REPO_ROOT/backend/data-scripts/data/popup-trends.json}"
MOCK_CROWD="${MOCK_CROWD:-0}"
REQUIRE_LIVE="${REQUIRE_LIVE:-0}"
PROBE_LIVE="${PROBE_LIVE:-$REQUIRE_LIVE}"
MOCK_LOG="${MOCK_LOG:-${TMPDIR:-/tmp}/nugulmap-season2-mock-crowd.log}"

mock_pid=""

cleanup() {
  if [[ -n "$mock_pid" ]] && kill -0 "$mock_pid" 2>/dev/null; then
    kill "$mock_pid" 2>/dev/null || true
    wait "$mock_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-30}"

  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for $label at $url" >&2
  tail -80 "$MOCK_LOG" >&2 || true
  return 1
}

if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "PORT $PORT is already in use" >&2
  exit 2
fi

if [[ "$MOCK_CROWD" == "1" ]]; then
  if lsof -nP -iTCP:"$MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "MOCK_PORT $MOCK_PORT is already in use" >&2
    exit 2
  fi

  python3 "$SCRIPT_DIR/mock-telecom-crowd-api.py" --port "$MOCK_PORT" >"$MOCK_LOG" 2>&1 &
  mock_pid="$!"
  wait_for_url "http://127.0.0.1:$MOCK_PORT/health" "mock TELECOM_CROWD"
  export TELECOM_CROWD_API_KEY="${TELECOM_CROWD_API_KEY:-mock-key}"
  if [[ -z "${TELECOM_CROWD_URL_TEMPLATE:-}" ]]; then
    export TELECOM_CROWD_URL_TEMPLATE="http://127.0.0.1:$MOCK_PORT/crowd?place={placeId}&area={seoulAreaCode}&name={placeName}"
  fi
fi

if [[ "$PROBE_LIVE" == "1" ]]; then
  "$SCRIPT_DIR/probe-season2-live-providers.py" --require-people-range
fi

cd "$API_ROOT"

SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}" \
APP_STORAGE_TYPE="${APP_STORAGE_TYPE:-local}" \
SPRING_SQL_INIT_MODE="${SPRING_SQL_INIT_MODE:-never}" \
SPRING_JPA_HIBERNATE_DDL_AUTO="${SPRING_JPA_HIBERNATE_DDL_AUTO:-create}" \
POPUP_TRENDS_FILE="$POPUP_TRENDS_FILE" \
./gradlew bootRun --args="--server.port=$PORT"
