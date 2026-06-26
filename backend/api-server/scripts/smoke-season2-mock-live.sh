#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$API_ROOT/../.." && pwd)"

API_PORT="${API_PORT:-18080}"
MOCK_PORT="${MOCK_PORT:-18081}"
POPUP_TRENDS_FILE="${POPUP_TRENDS_FILE:-$REPO_ROOT/backend/data-scripts/data/popup-trends.json}"
LOG_DIR="${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/season2-mock-live.XXXXXX")}"
MOCK_LOG="$LOG_DIR/mock-telecom-crowd.log"
API_LOG="$LOG_DIR/api-server.log"

mock_pid=""
api_pid=""

cleanup() {
  if [[ -n "$api_pid" ]] && kill -0 "$api_pid" 2>/dev/null; then
    kill "$api_pid" 2>/dev/null || true
    wait "$api_pid" 2>/dev/null || true
  fi
  if [[ -n "$mock_pid" ]] && kill -0 "$mock_pid" 2>/dev/null; then
    kill "$mock_pid" 2>/dev/null || true
    wait "$mock_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
  local delay_seconds="${4:-1}"

  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "Timed out waiting for $label at $url" >&2
  echo "--- mock log ---" >&2
  tail -80 "$MOCK_LOG" >&2 || true
  echo "--- api log ---" >&2
  tail -160 "$API_LOG" >&2 || true
  return 1
}

if lsof -nP -iTCP:"$API_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "API_PORT $API_PORT is already in use" >&2
  exit 2
fi

if lsof -nP -iTCP:"$MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "MOCK_PORT $MOCK_PORT is already in use" >&2
  exit 2
fi

mkdir -p "$LOG_DIR"
echo "Using logs at $LOG_DIR"

python3 "$SCRIPT_DIR/mock-telecom-crowd-api.py" --port "$MOCK_PORT" >"$MOCK_LOG" 2>&1 &
mock_pid="$!"
wait_for_url "http://127.0.0.1:$MOCK_PORT/health" "mock TELECOM_CROWD"

(
  cd "$REPO_ROOT"
  POPUP_TRENDS_FILE="$POPUP_TRENDS_FILE" \
  TELECOM_CROWD_API_KEY=mock-key \
  TELECOM_CROWD_URL_TEMPLATE="http://127.0.0.1:$MOCK_PORT/crowd?place={placeId}&area={seoulAreaCode}&name={placeName}" \
  SEOUL_CULTURE_API_KEY=culture-key \
  SEOUL_CULTURE_API_BASE_URL="http://127.0.0.1:$MOCK_PORT" \
  python3 "$SCRIPT_DIR/check-season2-readiness.py" \
    --require-live \
    --strict-popup-quality \
    --probe-live-provider \
    --probe-public-event-provider \
    --public-event-probe-limit 5
)

(
  cd "$API_ROOT"
  SPRING_PROFILES_ACTIVE=dev \
  APP_STORAGE_TYPE=local \
  SPRING_JPA_HIBERNATE_DDL_AUTO=create \
  SPRING_SQL_INIT_MODE=never \
  POPUP_TRENDS_FILE="$POPUP_TRENDS_FILE" \
  TELECOM_CROWD_API_KEY=mock-key \
  TELECOM_CROWD_URL_TEMPLATE="http://127.0.0.1:$MOCK_PORT/crowd?place={placeId}&area={seoulAreaCode}&name={placeName}" \
  SEOUL_CULTURE_API_KEY=culture-key \
  SEOUL_CULTURE_API_BASE_URL="http://127.0.0.1:$MOCK_PORT" \
  SEOUL_CULTURE_API_END_INDEX=5 \
  ./gradlew bootRun --args="--server.port=$API_PORT"
) >"$API_LOG" 2>&1 &
api_pid="$!"

wait_for_url "http://127.0.0.1:$API_PORT/api/insights/map" "NugulMap API"

"$SCRIPT_DIR/smoke-season2-insights.py" \
  --base-url "http://127.0.0.1:$API_PORT" \
  --require-live \
  --include-map-bootstrap

echo "OK season2 mock live smoke passed"
