#!/usr/bin/env python3
"""Serve deterministic, no-cache iOS UI-test fixtures."""

from __future__ import annotations

import argparse
import json
import sys
from threading import Lock
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


EXPECTED_AUTHORIZATION = "Bearer ui-test-access-token"
PROTECTED_PATHS = {"/api/auth/me", "/api/zones/my"}
KOREA_TIME_ZONE = timezone(timedelta(hours=9))


def make_time_stable(payload: object, request_path: str) -> object:
    if request_path != "/api/insights/map" or not isinstance(payload, dict):
        return payload

    today = datetime.now(KOREA_TIME_ZONE).date()
    end_date = today + timedelta(days=30)
    start_value = today.isoformat()
    end_value = end_date.isoformat()
    updated_value = datetime.now(KOREA_TIME_ZONE).isoformat(timespec="seconds")

    data = payload.get("data")
    if not isinstance(data, dict):
        return payload
    events_container = data.get("events")
    if isinstance(events_container, dict):
        events = events_container.get("events")
        if isinstance(events, list):
            for event in events:
                if isinstance(event, dict):
                    event["startDate"] = start_value
                    event["endDate"] = end_value
                    event["period"] = f"{start_value} ~ {end_value}"
                    event["collectedAt"] = updated_value
        events_container["updatedAt"] = updated_value
    hotplaces = data.get("hotplaces")
    if isinstance(hotplaces, dict):
        hotplaces["updatedAt"] = updated_value
    data["updatedAt"] = updated_value
    return payload


class FixtureHandler(BaseHTTPRequestHandler):
    fixture_root = Path.cwd()
    bounds_request_count = 0
    request_lock = Lock()

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        request_path = urlsplit(self.path).path
        if request_path == "/__test/bounds-request-count":
            with self.request_lock:
                count = type(self).bounds_request_count
            self._send_json(200, {"count": count})
            return
        if request_path == "/api/zones/bounds":
            with self.request_lock:
                type(self).bounds_request_count += 1
        if request_path in PROTECTED_PATHS:
            authorization = self.headers.get("Authorization", "")
            if authorization != EXPECTED_AUTHORIZATION:
                self._send_json(
                    401,
                    {"success": False, "message": "Authentication required", "data": None},
                )
                return

        fixture = (self.fixture_root / request_path.lstrip("/")).resolve()
        try:
            fixture.relative_to(self.fixture_root.resolve())
        except ValueError:
            self._send_json(404, {"success": False, "message": "Not found"})
            return

        if not fixture.is_file():
            self._send_json(404, {"success": False, "message": "Not found"})
            return

        try:
            payload = json.loads(fixture.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            self._send_json(500, {"success": False, "message": "Invalid fixture"})
            return
        payload = make_time_stable(payload, request_path)
        self._send_json(200, payload)

    def _send_json(self, status: int, payload: object) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:
        sys.stderr.write(f"{self.address_string()} - {format % args}\n")
        sys.stderr.flush()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    FixtureHandler.fixture_root = args.directory.resolve()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), FixtureHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
