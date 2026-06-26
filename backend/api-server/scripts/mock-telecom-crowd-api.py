#!/usr/bin/env python3
"""Local mock Season 2 live providers for smoke tests."""

from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


CROWD_FIXTURES: dict[str, tuple[int, int, str]] = {
    "hongdae": (30000, 36000, "붐빔"),
    "gangnam-station": (26000, 31000, "붐빔"),
    "lotte-world": (24000, 29000, "붐빔"),
    "seongsu": (22000, 27000, "붐빔"),
    "lotte-tower-lake": (18000, 23000, "약간 붐빔"),
}


def crowd_payload(place_id: str) -> dict[str, object]:
    min_people, max_people, crowd_level = CROWD_FIXTURES.get(place_id, (7000, 11000, "보통"))
    return {
        "data": {
            "crowdLevel": crowd_level,
            "crowdMessage": f"mock telecom crowd for {place_id}",
            "estimatedMinPeople": min_people,
            "estimatedMaxPeople": max_people,
            "sourcePlaceCode": f"mock-{place_id}",
            "updatedAt": "2026-06-18T14:58:00Z",
        }
    }


def culture_payload() -> dict[str, object]:
    return {
        "culturalEventInfo": {
            "row": [
                {
                    "CODENAME": "전시",
                    "TITLE": "성수 런타임 문화행사",
                    "DATE": "2026-08-21~2026-08-23",
                    "PLACE": "서울 성동구 성수동",
                    "STRTDATE": "2026-08-21 00:00:00.0",
                    "END_DATE": "2026-08-23 00:00:00.0",
                    "LOT": "127.0557",
                    "LAT": "37.5446",
                    "MAIN_IMG": "https://example.com/culture.jpg",
                    "HMPG_ADDR": "https://culture.seoul.go.kr/culture-event",
                },
                {
                    "CODENAME": "축제",
                    "TITLE": "잠실 호수 축제",
                    "DATE": "2026-09-10~2026-09-12",
                    "PLACE": "서울 송파구 석촌호수",
                    "STRTDATE": "2026-09-10 00:00:00.0",
                    "END_DATE": "2026-09-12 00:00:00.0",
                    "LOT": "127.1035",
                    "LAT": "37.5130",
                    "MAIN_IMG": "https://example.com/jamsil.jpg",
                    "HMPG_ADDR": "https://culture.seoul.go.kr/jamsil-event",
                },
            ]
        }
    }


class MockTelecomCrowdHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.respond_json({"ok": True})
            return
        if parsed.path == "/crowd":
            query = parse_qs(parsed.query)
            place_id = query.get("place", ["unknown"])[0]
            self.respond_json(crowd_payload(place_id))
            return
        if parsed.path.endswith("/json/culturalEventInfo/1/5") or "/json/culturalEventInfo/1/" in parsed.path:
            self.respond_json(culture_payload())
            return

        self.send_response(404)
        self.end_headers()

    def respond_json(self, payload: dict[str, object]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: object) -> None:
        print(fmt % args, flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run local mock TELECOM_CROWD and Seoul Culture APIs.")
    parser.add_argument("--host", default="127.0.0.1", help="Bind host, default: %(default)s")
    parser.add_argument("--port", type=int, default=18081, help="Bind port, default: %(default)s")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), MockTelecomCrowdHandler)
    print(
        f"mock Season 2 providers listening on http://{args.host}:{args.port}/crowd?place={{placeId}} "
        f"and http://{args.host}:{args.port}/culture-key/json/culturalEventInfo/1/5",
        flush=True,
    )
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
