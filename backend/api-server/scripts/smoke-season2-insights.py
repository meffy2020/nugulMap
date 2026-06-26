#!/usr/bin/env python3
"""Smoke-test Season 2 insight endpoints against a running API server."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin
from urllib.request import urlopen


@dataclass(frozen=True)
class SmokeResult:
    name: str
    freshness: str | None
    count: int
    is_live: bool


def fetch_json(base_url: str, path: str, query: dict[str, str | int | float]) -> dict[str, Any]:
    url = urljoin(base_url.rstrip("/") + "/", path.lstrip("/"))
    if query:
        url = f"{url}?{urlencode(query)}"

    try:
        with urlopen(url, timeout=8) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{path} returned HTTP {exc.code}: {detail[:300]}") from exc
    except URLError as exc:
        raise RuntimeError(f"Cannot reach API server at {base_url}: {exc.reason}") from exc


def payload_data(envelope: dict[str, Any]) -> dict[str, Any]:
    data = envelope.get("data")
    if not isinstance(data, dict):
        raise AssertionError("response envelope must contain object field 'data'")
    return data


def insight_map_data(base_url: str, query: dict[str, str | int | float]) -> dict[str, Any]:
    return payload_data(fetch_json(base_url, "/api/insights/map", query))


def smoke_hotplaces(base_url: str) -> SmokeResult:
    data = insight_map_data(
        base_url,
        {
            "keyword": "롯데",
            "hotplaceLimit": 3,
            "eventLimit": 1,
            "minLat": 37.50,
            "maxLat": 37.53,
            "minLng": 127.08,
            "maxLng": 127.11,
        },
    ).get("hotplaces", {})
    places = data.get("places")
    if not isinstance(places, list) or not places:
        raise AssertionError("hotplaces must include at least one 롯데/잠실 candidate")

    first = places[0]
    if not isinstance(first, dict):
        raise AssertionError("hotplace item must be an object")

    name = str(first.get("name", ""))
    if "롯데" not in name and "잠실" not in name:
        raise AssertionError(f"expected 롯데/잠실 hotplace first, got {name!r}")

    for field in ("latitude", "longitude"):
        if not isinstance(first.get(field), (int, float)):
            raise AssertionError(f"hotplace item must include numeric {field}")

    if data.get("dataFreshness") == "LIVE_OR_PARTIAL":
        source = first.get("source")
        has_count_range = isinstance(first.get("estimatedMinPeople"), int) and isinstance(first.get("estimatedMaxPeople"), int)
        if source in {"SEOUL_CITYDATA", "TELECOM_CROWD"} and not has_count_range:
            raise AssertionError("live 롯데/잠실 hotplace must include estimatedMinPeople and estimatedMaxPeople")

    return SmokeResult("hotplaces", data.get("dataFreshness"), len(places), data.get("dataFreshness") == "LIVE_OR_PARTIAL")


def smoke_hot_now(base_url: str) -> SmokeResult:
    data = insight_map_data(
        base_url,
        {
            "keyword": "hot-now",
            "hotplaceLimit": 5,
            "eventLimit": 1,
            "minLat": 37.49,
            "maxLat": 37.57,
            "minLng": 126.90,
            "maxLng": 127.11,
        },
    ).get("hotplaces", {})
    places = data.get("places")
    if not isinstance(places, list) or not places:
        raise AssertionError("hot-now query must include at least one citywide hotplace candidate")

    first = places[0]
    if not isinstance(first, dict):
        raise AssertionError("hot-now item must be an object")

    names = {str(place.get("name", "")) for place in places if isinstance(place, dict)}
    expected_names = {"홍대입구", "성수동 카페거리", "강남역", "롯데월드·잠실"}
    if not names.intersection(expected_names):
        raise AssertionError(f"expected citywide hot-now candidates, got {sorted(names)!r}")

    for field in ("id", "name", "crowdLevel", "latitude", "longitude", "source"):
        if first.get(field) in (None, ""):
            raise AssertionError(f"hot-now item must include {field}")

    return SmokeResult("hot-now", data.get("dataFreshness"), len(places), data.get("dataFreshness") == "LIVE_OR_PARTIAL")


def smoke_events(base_url: str) -> SmokeResult:
    data = insight_map_data(
        base_url,
        {
            "hotplaceLimit": 1,
            "eventLimit": 5,
            "minLat": 37.48,
            "maxLat": 37.60,
            "minLng": 126.88,
            "maxLng": 127.12,
        },
    ).get("events", {})
    events = data.get("events")
    if not isinstance(events, list) or not events:
        raise AssertionError("events must include at least one popup/event/festival candidate")

    first = events[0]
    if not isinstance(first, dict):
        raise AssertionError("event item must be an object")

    for field in ("title", "latitude", "longitude"):
        if first.get(field) in (None, ""):
            raise AssertionError(f"event item must include {field}")

    freshness = data.get("dataFreshness")
    return SmokeResult("events", freshness, len(events), freshness in {"LIVE_OR_PARTIAL", "CRAWLED_OR_PARTIAL"})


def smoke_seongsu_popup(base_url: str) -> SmokeResult:
    data = insight_map_data(
        base_url,
        {
            "keyword": "성수",
            "hotplaceLimit": 1,
            "eventLimit": 5,
            "minLat": 37.53,
            "maxLat": 37.56,
            "minLng": 127.04,
            "maxLng": 127.07,
        },
    ).get("events", {})
    events = data.get("events")
    if not isinstance(events, list) or not events:
        raise AssertionError("성수 popup query must include at least one popup/event candidate")

    first = events[0]
    if not isinstance(first, dict):
        raise AssertionError("성수 popup item must be an object")

    title = str(first.get("title", ""))
    address = str(first.get("address", ""))
    if "성수" not in title and "성수" not in address:
        raise AssertionError(f"expected 성수 popup/event result, got title={title!r}, address={address!r}")

    freshness = data.get("dataFreshness")
    return SmokeResult("seongsu-popup", freshness, len(events), freshness in {"LIVE_OR_PARTIAL", "CRAWLED_OR_PARTIAL"})


def smoke_generic_event_discovery(base_url: str) -> SmokeResult:
    data = insight_map_data(
        base_url,
        {
            "keyword": "요즘 핫한 팝업 행사",
            "hotplaceLimit": 1,
            "eventLimit": 5,
            "minLat": 37.48,
            "maxLat": 37.60,
            "minLng": 126.88,
            "maxLng": 127.12,
        },
    ).get("events", {})
    events = data.get("events")
    if not isinstance(events, list) or not events:
        raise AssertionError("generic hot popup/event discovery query must include event candidates")

    first = events[0]
    if not isinstance(first, dict):
        raise AssertionError("generic event discovery item must be an object")

    for field in ("title", "kind", "latitude", "longitude", "source"):
        if first.get(field) in (None, ""):
            raise AssertionError(f"generic event discovery item must include {field}")

    freshness = data.get("dataFreshness")
    return SmokeResult("event-discovery", freshness, len(events), freshness in {"LIVE_OR_PARTIAL", "CRAWLED_OR_PARTIAL"})


def smoke_map_bootstrap(base_url: str) -> SmokeResult:
    data = payload_data(
        fetch_json(
            base_url,
            "/api/zones/bounds",
            {
                "minLat": 37.52,
                "maxLat": 37.59,
                "minLng": 126.93,
                "maxLng": 127.02,
            },
        )
    )
    zones = data.get("zones")
    if not isinstance(zones, list):
        raise AssertionError("zones/bounds must include list field data.zones")

    return SmokeResult("map-bootstrap", "HTTP_OK", len(zones), True)


def smoke_insight_map(base_url: str) -> SmokeResult:
    data = payload_data(
        fetch_json(
            base_url,
            "/api/insights/map",
            {
                "hotplaceLimit": 5,
                "eventLimit": 5,
                "minLat": 37.48,
                "maxLat": 37.60,
                "minLng": 126.88,
                "maxLng": 127.12,
            },
        )
    )

    hotplaces = data.get("hotplaces")
    events = data.get("events")
    status = data.get("status")
    if not isinstance(hotplaces, dict) or not isinstance(events, dict) or not isinstance(status, dict):
        raise AssertionError("insight map must include hotplaces, events, and status objects")

    place_items = hotplaces.get("places")
    event_items = events.get("events")
    if not isinstance(place_items, list):
        raise AssertionError("insight map hotplaces.places must be a list")
    if not isinstance(event_items, list) or not event_items:
        raise AssertionError("insight map events.events must include event discovery candidates")

    for field in ("seoulCultureApiKeyConfigured", "seoulCultureApi", "popupTrends"):
        if field not in status:
            raise AssertionError(f"insight map status must include {field}")
    if not isinstance(status.get("seoulCultureApi"), dict):
        raise AssertionError("insight map status.seoulCultureApi must be an object")

    event_sources = {
        str(item.get("source"))
        for item in event_items
        if isinstance(item, dict) and item.get("source") not in (None, "")
    }
    if "SEOUL_CULTURE_API" in event_sources and status["seoulCultureApi"].get("qualityStatus") not in {"OK", "NOT_CONFIGURED"}:
        raise AssertionError("insight map Seoul Culture events must have a coherent seoulCultureApi provider status")

    hotplace_live = hotplaces.get("dataFreshness") == "LIVE_OR_PARTIAL"
    event_live = events.get("dataFreshness") in {"LIVE_OR_PARTIAL", "CRAWLED_OR_PARTIAL"}
    return SmokeResult(
        "insight-map",
        (
            f"hotplace={hotplaces.get('dataFreshness')},"
            f"event={events.get('dataFreshness')},"
            f"seoulCulture={status['seoulCultureApi'].get('qualityStatus')}"
        ),
        len(place_items) + len(event_items),
        hotplace_live and event_live,
    )


def smoke_status(base_url: str) -> SmokeResult:
    data = insight_map_data(base_url, {"hotplaceLimit": 1, "eventLimit": 1}).get("status", {})

    for field in (
        "seoulCityDataKeyConfigured",
        "telecomCrowdKeyConfigured",
        "telecomCrowdUrlTemplateConfigured",
        "ktoTourApiKeyConfigured",
        "seoulCultureApiKeyConfigured",
        "hotplaceMode",
        "eventMode",
        "seoulCityData",
        "telecomCrowd",
        "ktoTourApi",
        "seoulCultureApi",
        "popupTrends",
    ):
        if field not in data:
            raise AssertionError(f"status response must include {field}")

    for field in (
        "seoulCityDataKeyConfigured",
        "telecomCrowdKeyConfigured",
        "telecomCrowdUrlTemplateConfigured",
        "ktoTourApiKeyConfigured",
        "seoulCultureApiKeyConfigured",
    ):
        if not isinstance(data.get(field), bool):
            raise AssertionError(f"status {field} must be a boolean")

    for field in ("seoulCityData", "telecomCrowd", "ktoTourApi", "seoulCultureApi"):
        provider_status = data.get(field)
        if not isinstance(provider_status, dict):
            raise AssertionError(f"status {field} must be an object")
        for provider_field in ("configured", "qualityStatus", "detail"):
            if provider_field not in provider_status:
                raise AssertionError(f"status {field}.{provider_field} is required")

    popup_trends = data.get("popupTrends")
    if not isinstance(popup_trends, dict):
        raise AssertionError("status popupTrends must be an object")

    record_count = popup_trends.get("recordCount")
    if not isinstance(record_count, int):
        raise AssertionError("status popupTrends.recordCount must be an integer")

    seoul_status = data.get("seoulCityData")
    telecom_status = data.get("telecomCrowd")
    culture_status = data.get("seoulCultureApi")
    seoul_live = (
        data.get("seoulCityDataKeyConfigured") is True
        and isinstance(seoul_status, dict)
        and seoul_status.get("qualityStatus") == "OK"
    )
    telecom_live = (
        data.get("telecomCrowdKeyConfigured") is True
        and data.get("telecomCrowdUrlTemplateConfigured") is True
        and isinstance(telecom_status, dict)
        and telecom_status.get("qualityStatus") == "OK"
    )
    hotplace_live = data.get("hotplaceMode") == "LIVE_READY" and (seoul_live or telecom_live)
    culture_live = (
        data.get("seoulCultureApiKeyConfigured") is True
        and isinstance(culture_status, dict)
        and culture_status.get("qualityStatus") == "OK"
    )
    popup_ready = popup_trends.get("qualityStatus") == "OK" and record_count > 0
    event_ready = data.get("eventMode") == "LIVE_OR_CRAWLED_READY" and (culture_live or popup_ready or data.get("ktoTourApi", {}).get("qualityStatus") == "OK")
    freshness = (
        f"hotplace={data.get('hotplaceMode')},"
        f"seoul={seoul_status.get('qualityStatus') if isinstance(seoul_status, dict) else 'missing'},"
        f"telecom={telecom_status.get('qualityStatus') if isinstance(telecom_status, dict) else 'missing'},"
        f"event={data.get('eventMode')},"
        f"culture={culture_status.get('qualityStatus') if isinstance(culture_status, dict) else 'missing'},"
        f"popup={popup_trends.get('qualityStatus')}"
    )
    return SmokeResult("status", freshness, record_count, hotplace_live and event_ready)


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke-test NugulMap Season 2 insight API endpoints.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="API server origin, default: %(default)s")
    parser.add_argument(
        "--require-live",
        action="store_true",
        help="Fail unless at least one live crowd provider and live/crawled event sources are active.",
    )
    parser.add_argument(
        "--include-map-bootstrap",
        action="store_true",
        help="Also verify /api/zones/bounds returns successfully for app map bootstrap.",
    )
    args = parser.parse_args()

    try:
        results = [
            smoke_hotplaces(args.base_url),
            smoke_hot_now(args.base_url),
            smoke_events(args.base_url),
            smoke_seongsu_popup(args.base_url),
            smoke_generic_event_discovery(args.base_url),
            smoke_insight_map(args.base_url),
            smoke_status(args.base_url),
        ]
        if args.include_map_bootstrap:
            results.append(smoke_map_bootstrap(args.base_url))
    except (AssertionError, RuntimeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    for result in results:
        mode = "live" if result.is_live else "fallback"
        print(f"OK {result.name}: count={result.count} freshness={result.freshness or 'unknown'} mode={mode}")

    if args.require_live:
        stale = [result.name for result in results if not result.is_live]
        if stale:
            print(f"FAIL: live mode required but these sources are fallback-only: {', '.join(stale)}", file=sys.stderr)
            return 2

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
