#!/usr/bin/env python3
"""Probe real Season 2 live crowd providers before starting the API server."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import deque
from dataclasses import dataclass
from typing import Any


LOTTE_WORLD = {
    "placeId": "lotte-world",
    "placeName": "롯데월드·잠실",
    "seoulAreaCode": "POI005",
    "seoulAreaName": "잠실 관광특구",
    "lat": "37.5111",
    "lng": "127.0982",
}
JAMSIL_CITYDATA_QUERIES = (
    "POI005",
    "잠실 관광특구",
    "POI118",
    "잠실새내역",
    "POI109",
    "잠실종합운동장",
)
INTEGER_PATTERN = re.compile(r"\d[\d,]*")


@dataclass(frozen=True)
class CrowdProbe:
    provider: str
    ok: bool
    level: str | None = None
    min_people: int | None = None
    max_people: int | None = None
    place_code: str | None = None
    detail: str | None = None

    @property
    def has_people_range(self) -> bool:
        return isinstance(self.min_people, int) and isinstance(self.max_people, int)


def status_line(ok: bool, label: str, detail: str) -> None:
    prefix = "OK" if ok else "WARN"
    print(f"{prefix} {label}: {detail}")


def fetch_bytes(url: str, *, headers: dict[str, str] | None = None, timeout: int = 8) -> bytes:
    request = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def parse_int(value: Any) -> int | None:
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if value is None:
        return None

    match = INTEGER_PATTERN.search(str(value))
    if not match:
        return None
    try:
        return int(match.group().replace(",", ""))
    except ValueError:
        return None


def parse_range(value: Any) -> tuple[int | None, int | None]:
    if isinstance(value, (int, float)):
        count = int(value)
        return count, count
    if value is None:
        return None, None

    numbers: list[int] = []
    for match in INTEGER_PATTERN.finditer(str(value)):
        try:
            numbers.append(int(match.group().replace(",", "")))
        except ValueError:
            continue
        if len(numbers) == 2:
            break

    if not numbers:
        return None, None
    if len(numbers) == 1:
        return numbers[0], numbers[0]
    return min(numbers), max(numbers)


def first_string(data: dict[str, Any], keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = data.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def first_int(data: dict[str, Any], keys: tuple[str, ...]) -> int | None:
    for key in keys:
        parsed = parse_int(data.get(key))
        if parsed is not None:
            return parsed
    return None


def first_range(data: dict[str, Any], keys: tuple[str, ...]) -> tuple[int | None, int | None]:
    for key in keys:
        parsed = parse_range(data.get(key))
        if parsed != (None, None):
            return parsed
    return None, None


def json_candidates(payload: Any) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    queue: deque[Any] = deque([payload])
    while queue and len(candidates) < 80:
        current = queue.popleft()
        if isinstance(current, dict):
            candidates.append(current)
            for key in ("data", "result", "body", "items", "item", "features", "properties", "crowd", "congestion"):
                child = current.get(key)
                if isinstance(child, (dict, list)):
                    queue.append(child)
        elif isinstance(current, list):
            for item in current[:20]:
                if isinstance(item, (dict, list)):
                    queue.append(item)
    return candidates


def has_crowd_signal(data: dict[str, Any]) -> bool:
    min_range, max_range = first_range(data, ("estimatedPeople", "population", "populationRange", "people", "peopleRange", "visitorCount"))
    return any(
        (
            first_string(data, ("crowdLevel", "congestionLevel", "congestion", "level")),
            first_int(data, ("estimatedMinPeople", "minPeople", "populationMin", "peopleMin")),
            first_int(data, ("estimatedMaxPeople", "maxPeople", "populationMax", "peopleMax")),
            min_range,
            max_range,
        )
    )


def parse_telecom_payload(payload: Any) -> CrowdProbe:
    data = next((candidate for candidate in json_candidates(payload) if has_crowd_signal(candidate)), None)
    if data is None:
        return CrowdProbe(provider="TELECOM_CROWD", ok=False, detail="response has no crowd level or people range")

    min_people = first_int(data, ("estimatedMinPeople", "minPeople", "populationMin", "peopleMin"))
    max_people = first_int(data, ("estimatedMaxPeople", "maxPeople", "populationMax", "peopleMax"))
    range_min, range_max = first_range(data, ("estimatedPeople", "population", "populationRange", "people", "peopleRange", "visitorCount"))
    return CrowdProbe(
        provider="TELECOM_CROWD",
        ok=True,
        level=first_string(data, ("crowdLevel", "congestionLevel", "congestion", "level")),
        min_people=min_people if min_people is not None else range_min,
        max_people=max_people if max_people is not None else range_max,
        place_code=first_string(data, ("sourcePlaceCode", "placeId", "poiId", "id")),
        detail=first_string(data, ("crowdMessage", "message", "congestionMessage")),
    )


def resolve_template(template: str) -> str:
    resolved = template
    for key, value in LOTTE_WORLD.items():
        resolved = resolved.replace("{" + key + "}", urllib.parse.quote(value))
    api_key = os.environ.get("TELECOM_CROWD_API_KEY", "").strip()
    resolved = resolved.replace("{apiKey}", urllib.parse.quote(api_key))
    return resolved


def telecom_headers(api_key: str, header: str) -> dict[str, str]:
    header_name = header.strip()
    if not header_name or header_name.lower() in {"none", "off"}:
        return {}
    return {header_name: api_key}


def probe_telecom(timeout: int) -> CrowdProbe | None:
    api_key = os.environ.get("TELECOM_CROWD_API_KEY", "").strip()
    template = os.environ.get("TELECOM_CROWD_URL_TEMPLATE", "").strip()
    header = os.environ.get("TELECOM_CROWD_API_KEY_HEADER", "appKey").strip() or "appKey"

    if not api_key and not template:
        status_line(False, "TELECOM_CROWD", "TELECOM_CROWD_API_KEY and TELECOM_CROWD_URL_TEMPLATE are not configured")
        return None
    if not api_key or not template:
        status_line(False, "TELECOM_CROWD", "both TELECOM_CROWD_API_KEY and TELECOM_CROWD_URL_TEMPLATE are required")
        return CrowdProbe(provider="TELECOM_CROWD", ok=False, detail="incomplete configuration")

    try:
        body = fetch_bytes(resolve_template(template), headers=telecom_headers(api_key, header), timeout=timeout)
        probe = parse_telecom_payload(json.loads(body.decode("utf-8")))
    except Exception as error:
        return CrowdProbe(provider="TELECOM_CROWD", ok=False, detail=f"request failed: {type(error).__name__}")

    return probe


def xml_text(root: ET.Element, tag_name: str) -> str | None:
    node = root.find(f".//{tag_name}")
    if node is None or node.text is None:
        return None
    text = node.text.strip()
    return text or None


def parse_citydata_xml(xml: bytes, query: str) -> CrowdProbe:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return CrowdProbe(provider="SEOUL_CITYDATA", ok=False, detail=f"{query}: invalid XML")

    level = xml_text(root, "AREA_CONGEST_LVL")
    if not level:
        result_code = xml_text(root, "CODE")
        message = xml_text(root, "MESSAGE")
        detail = f"{query}: no AREA_CONGEST_LVL"
        if result_code or message:
            detail += f" ({result_code or 'unknown'} {message or ''})"
        return CrowdProbe(provider="SEOUL_CITYDATA", ok=False, detail=detail)

    return CrowdProbe(
        provider="SEOUL_CITYDATA",
        ok=True,
        level=level,
        min_people=parse_int(xml_text(root, "AREA_PPLTN_MIN")),
        max_people=parse_int(xml_text(root, "AREA_PPLTN_MAX")),
        place_code=xml_text(root, "AREA_NM") or query,
        detail=xml_text(root, "AREA_CONGEST_MSG"),
    )


def probe_citydata(timeout: int) -> CrowdProbe | None:
    api_key = os.environ.get("SEOUL_CITYDATA_API_KEY", "").strip()
    if not api_key:
        status_line(False, "SEOUL_CITYDATA", "SEOUL_CITYDATA_API_KEY is not configured")
        return None

    last_probe: CrowdProbe | None = None
    for query in JAMSIL_CITYDATA_QUERIES:
        encoded_query = urllib.parse.quote(query)
        url = f"http://openapi.seoul.go.kr:8088/{urllib.parse.quote(api_key)}/xml/citydata/1/5/{encoded_query}"
        try:
            probe = parse_citydata_xml(fetch_bytes(url, timeout=timeout), query)
        except Exception as error:
            probe = CrowdProbe(provider="SEOUL_CITYDATA", ok=False, detail=f"{query}: request failed: {type(error).__name__}")
        if probe.ok:
            return probe
        last_probe = probe
    return last_probe or CrowdProbe(provider="SEOUL_CITYDATA", ok=False, detail="no Jamsil aliases returned crowd data")


def describe_probe(probe: CrowdProbe) -> str:
    people = ""
    if probe.has_people_range:
        people = f", people={probe.min_people}-{probe.max_people}"
    place = f", place={probe.place_code}" if probe.place_code else ""
    detail = f", detail={probe.detail}" if probe.detail else ""
    return f"level={probe.level or 'unknown'}{people}{place}{detail}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Probe real Season 2 Lotte World/Jamsil live crowd providers.")
    parser.add_argument("--timeout", type=int, default=8, help="HTTP timeout seconds, default: %(default)s")
    parser.add_argument(
        "--require-people-range",
        action="store_true",
        help="Fail unless a successful provider includes estimatedMinPeople and estimatedMaxPeople.",
    )
    args = parser.parse_args()

    probes = [probe for probe in (probe_telecom(args.timeout), probe_citydata(args.timeout)) if probe is not None]
    if not probes:
        print("FAIL: no live crowd provider is configured", file=sys.stderr)
        return 2

    ok_probes = [probe for probe in probes if probe.ok and (probe.has_people_range or not args.require_people_range)]
    for probe in probes:
        ok = probe.ok and (probe.has_people_range or not args.require_people_range)
        reason = describe_probe(probe) if probe.ok else probe.detail or "provider failed"
        if probe.ok and args.require_people_range and not probe.has_people_range:
            reason += ", missing people range"
        status_line(ok, probe.provider, reason)

    if not ok_probes:
        print("FAIL: no configured provider returned a valid Lotte World/Jamsil live crowd signal", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
