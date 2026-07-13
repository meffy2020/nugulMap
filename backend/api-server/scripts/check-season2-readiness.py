#!/usr/bin/env python3
"""Check local readiness for NugulMap live hotplace/event insights."""

from __future__ import annotations

import argparse
import ipaddress
import json
import os
import subprocess
import sys
import re
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_POPUP_FILE = REPO_ROOT / "backend" / "data-scripts" / "data" / "popup-trends.json"
SEOUL_BOUNDS = {
    "min_lat": 37.40,
    "max_lat": 37.72,
    "min_lng": 126.76,
    "max_lng": 127.20,
}
SEONGSU_BOUNDS = {
    "min_lat": 37.532,
    "max_lat": 37.558,
    "min_lng": 127.032,
    "max_lng": 127.072,
}
SEONGSU_LOCATION_TERMS = ("성수", "서울숲", "연무장", "성동구")
POPUP_TERM_RE = re.compile(r"(?<![a-z])popup(?![a-z])", re.IGNORECASE)
TELECOM_TEMPLATE_PLACEHOLDERS = {
    "placeId",
    "placeName",
    "seoulAreaCode",
    "seoulAreaName",
    "lat",
    "lng",
    "apiKey",
}
TELECOM_TEMPLATE_REQUIRED_LOCATION_PLACEHOLDERS = {
    "placeId",
    "placeName",
    "lat",
    "lng",
    "seoulAreaCode",
    "seoulAreaName",
}
TELECOM_URL_PLACEHOLDER_RE = re.compile(r"\{([A-Za-z0-9_]+)\}")
TELECOM_API_KEY_HEADER_DEFAULT = "appkey"
DEFAULT_POPUP_MAX_AGE_HOURS = 24


def status_line(ok: bool, label: str, detail: str) -> None:
    prefix = "OK" if ok else "WARN"
    print(f"{prefix} {label}: {detail}")


def has_env(name: str) -> bool:
    return bool(os.environ.get(name, "").strip())


def positive_env_int(name: str, fallback: int) -> int:
    try:
        value = int(os.environ.get(name, str(fallback)))
    except ValueError:
        return fallback
    return value if value > 0 else fallback


def parse_items(payload: Any) -> list[Any] | None:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict) and isinstance(payload.get("items"), list):
        return payload["items"]
    return None


def parse_collected_at(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    text = value.strip()
    if text.endswith("Z"):
        text = f"{text[:-1]}+00:00"
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def safe_detail_url(item: dict[str, Any]) -> str | None:
    value = item.get("detailUrl")
    if not value and item.get("collectionMode") is None:
        # Season 2 crawler compatibility: URLs used to be stored as identifiers.
        value = item.get("sourceContentId")
    return safe_http_url(value)


def validate_telecom_template(
    url_template: str,
    key_header: str,
) -> tuple[bool, list[str]]:
    if not url_template:
        return False, ["TELECOM_CROWD_URL_TEMPLATE is empty"]

    placeholders = set(TELECOM_URL_PLACEHOLDER_RE.findall(url_template))
    issues: list[str] = []

    unknown = [name for name in sorted(placeholders) if name not in TELECOM_TEMPLATE_PLACEHOLDERS]
    if unknown:
        issues.append(f"unknown placeholders: {', '.join(unknown)}")

    if not (placeholders & TELECOM_TEMPLATE_REQUIRED_LOCATION_PLACEHOLDERS):
        issues.append(
            "template must include at least one location placeholder: "
            f"{', '.join(sorted(TELECOM_TEMPLATE_REQUIRED_LOCATION_PLACEHOLDERS))}"
        )

    header_mode = (key_header or TELECOM_API_KEY_HEADER_DEFAULT).strip().lower()
    if header_mode in {"none", "off"} and "apiKey" not in placeholders:
        issues.append("TELECOM_CROWD_API_KEY_HEADER is none/off, so {apiKey} must be included in URL template")

    return len(issues) == 0, issues


def is_in_bounds(item: dict[str, Any], bounds: dict[str, float]) -> bool:
    try:
        latitude = float(item.get("latitude"))
        longitude = float(item.get("longitude"))
    except (TypeError, ValueError):
        return False
    return (
        bounds["min_lat"] <= latitude <= bounds["max_lat"]
        and bounds["min_lng"] <= longitude <= bounds["max_lng"]
    )


def is_in_seoul_bounds(item: dict[str, Any]) -> bool:
    return is_in_bounds(item, SEOUL_BOUNDS)


def parse_event_date(value: Any) -> date | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        return date.fromisoformat(value.strip())
    except ValueError:
        return None


def is_verified_seongsu_popup_record(item: dict[str, Any]) -> bool:
    title = str(item.get("title") or "").strip()
    address = str(item.get("address") or "").strip()
    start_date = parse_event_date(item.get("startDate"))
    end_date = parse_event_date(item.get("endDate"))
    location_text = f"{title} {address}".lower()
    has_popup_term = "팝업" in title or POPUP_TERM_RE.search(title) is not None

    return (
        item.get("source") == "SEOUL_CULTURE_API"
        and item.get("collectionMode") == "NETWORK"
        and str(item.get("kind") or "").strip().lower() == "popup"
        and has_popup_term
        and any(term in location_text for term in SEONGSU_LOCATION_TERMS)
        and is_in_bounds(item, SEONGSU_BOUNDS)
        and start_date is not None
        and end_date is not None
        and start_date <= end_date
        and bool(address)
        and safe_detail_url(item) is not None
        and bool(str(item.get("attribution") or "").strip())
        and bool(str(item.get("license") or "").strip())
        and safe_http_url(item.get("licenseUrl")) is not None
        and item.get("publicationPolicy") == "allowed_with_attribution"
    )


def safe_http_url(value: Any) -> str | None:
    if not isinstance(value, str) or not value.strip():
        return None
    parsed = urllib.parse.urlparse(value.strip())
    host = (parsed.hostname or "").strip().lower().rstrip(".")
    if (
        parsed.scheme.lower() not in {"http", "https"}
        or not parsed.netloc
        or not host
        or parsed.username is not None
        or parsed.password is not None
        or not is_public_link_host(host)
    ):
        return None
    return value.strip()


def is_public_link_host(host: str) -> bool:
    normalized = host.strip().lower().strip("[]").rstrip(".")
    if (
        normalized == "localhost"
        or normalized.endswith(".localhost")
        or normalized.endswith(".local")
        or normalized.endswith(".internal")
    ):
        return False
    try:
        address = ipaddress.ip_address(normalized)
    except ValueError:
        return True
    return not (
        address.is_private
        or address.is_loopback
        or address.is_link_local
        or address.is_multicast
        or address.is_reserved
        or address.is_unspecified
    )


def popup_quality_errors(items: list[Any], max_age_hours: int | None) -> list[str]:
    errors: list[str] = []
    now = datetime.now(timezone.utc)

    for index, item in enumerate(items):
        if not isinstance(item, dict):
            errors.append(f"item[{index}] must be an object")
            continue

        for field in (
            "id",
            "title",
            "kind",
            "startDate",
            "endDate",
            "latitude",
            "longitude",
            "address",
            "source",
            "detailUrl",
            "collectedAt",
        ):
            if item.get(field) in (None, ""):
                errors.append(f"item[{index}] missing {field}")

        if not is_verified_seongsu_popup_record(item):
            errors.append(
                f"item[{index}] is not a verified Seoul Culture Seongsu popup "
                "with dates, address, individual link, and attribution"
            )

        if max_age_hours is not None:
            collected_at = parse_collected_at(item.get("collectedAt"))
            if collected_at is None:
                errors.append(f"item[{index}] missing valid collectedAt")
            else:
                age_hours = (now - collected_at).total_seconds() / 3600
                if age_hours > max_age_hours:
                    errors.append(f"item[{index}] collectedAt is {age_hours:.1f}h old")

    return errors


def check_popup_file(path: Path, *, strict_quality: bool, max_age_hours: int | None) -> bool:
    if not path.exists():
        status_line(False, "popup_trends_file", f"missing {path}")
        return False

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        status_line(False, "popup_trends_file", f"invalid JSON: {exc}")
        return False

    items = parse_items(payload)
    if not isinstance(items, list):
        status_line(False, "popup_trends_file", "JSON must contain items[]")
        return False
    if not items:
        status_line(True, "popup_trends_quality", "valid empty items[]; no verified popup is currently publishable")
        status_line(True, "popup_trends_file", f"0 items at {path}")
        return True

    errors = popup_quality_errors(items, max_age_hours)
    if errors:
        detail = "; ".join(errors[:5])
        if len(errors) > 5:
            detail += f"; +{len(errors) - 5} more"
        status_line(False, "popup_trends_quality", detail)
        if strict_quality or max_age_hours is not None:
            return False
    else:
        status_line(
            True,
            "popup_trends_quality",
            "verified Seongsu popup fields, bounds, individual links, rights, and freshness checks passed",
        )

    status_line(True, "popup_trends_file", f"{len(items)} items at {path}")
    return True


def has_public_event_api_records(path: Path, max_age_hours: int | None) -> bool:
    if not path.exists():
        return False

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False

    items = parse_items(payload)
    if not isinstance(items, list):
        return False

    now = datetime.now(timezone.utc)
    for item in items:
        if not isinstance(item, dict):
            continue
        if not is_verified_seongsu_popup_record(item):
            continue
        if max_age_hours is not None:
            collected_at = parse_collected_at(item.get("collectedAt"))
            if collected_at is None or (now - collected_at).total_seconds() > max_age_hours * 3600:
                continue
        return True
    return False


def run_smoke(base_url: str, require_live: bool) -> int:
    script = Path(__file__).with_name("smoke-season2-insights.py")
    command = [sys.executable, str(script), "--base-url", base_url]
    if require_live:
        command.append("--require-live")

    result = subprocess.run(command, text=True, capture_output=True, check=False)
    if result.stdout.strip():
        print(result.stdout.strip())
    if result.stderr.strip():
        print(result.stderr.strip(), file=sys.stderr)
    return result.returncode


def run_provider_probe(timeout: int) -> int:
    script = Path(__file__).with_name("probe-season2-live-providers.py")
    command = [sys.executable, str(script), "--require-people-range", "--timeout", str(timeout)]
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    if result.stdout.strip():
        print(result.stdout.strip())
    if result.stderr.strip():
        print(result.stderr.strip(), file=sys.stderr)
    return result.returncode


def seoul_culture_rows(payload: Any) -> list[dict[str, Any]]:
    if not isinstance(payload, dict):
        return []

    candidates = [
        payload.get("culturalEventInfo"),
        payload.get("CultureEventInfo"),
        payload,
    ]
    for candidate in candidates:
        if isinstance(candidate, dict) and isinstance(candidate.get("row"), list):
            return [item for item in candidate["row"] if isinstance(item, dict)]
    return []


def seoul_culture_probe_item_ok(item: dict[str, Any]) -> bool:
    title = str(item.get("TITLE") or item.get("title") or "").strip()
    address = str(
        item.get("PLACE")
        or item.get("place")
        or item.get("address")
        or item.get("GUNAME")
        or ""
    ).strip()
    latitude = item.get("LAT") or item.get("latitude")
    longitude = item.get("LOT") or item.get("longitude")
    start_date = parse_event_date(str(item.get("STRTDATE") or item.get("startDate") or "")[:10])
    end_date = parse_event_date(str(item.get("END_DATE") or item.get("endDate") or "")[:10])
    detail_url = safe_http_url(
        item.get("ORG_LINK")
        or item.get("HMPG_ADDR")
        or item.get("detailUrl")
        or item.get("url")
    )
    location_text = f"{title} {address}".lower()
    return (
        ("팝업" in title or POPUP_TERM_RE.search(title) is not None)
        and any(term in location_text for term in SEONGSU_LOCATION_TERMS)
        and is_in_bounds({"latitude": latitude, "longitude": longitude}, SEONGSU_BOUNDS)
        and start_date is not None
        and end_date is not None
        and start_date <= end_date
        and bool(address)
        and detail_url is not None
    )


def run_public_event_provider_probe(timeout: int, limit: int) -> int:
    api_key = os.environ.get("SEOUL_CULTURE_API_KEY", "").strip()
    if not api_key:
        status_line(False, "SEOUL_CULTURE_API_PROBE", "SEOUL_CULTURE_API_KEY is not configured")
        return 2

    normalized_limit = max(1, min(limit, 100))
    base_url = os.environ.get("SEOUL_CULTURE_API_BASE_URL", "http://openapi.seoul.go.kr:8088").strip()
    if not base_url:
        base_url = "http://openapi.seoul.go.kr:8088"
    url = f"{base_url.rstrip('/')}/{urllib.parse.quote(api_key)}/json/culturalEventInfo/1/{normalized_limit}/"
    try:
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception as error:
        status_line(False, "SEOUL_CULTURE_API_PROBE", f"request failed: {type(error).__name__}")
        return 2

    rows = seoul_culture_rows(payload)
    usable_count = sum(1 for item in rows if seoul_culture_probe_item_ok(item))
    if usable_count == 0:
        result = payload.get("RESULT") if isinstance(payload, dict) else None
        detail = "no verified Seongsu popup rows with dates, address, coordinates, and individual link"
        if isinstance(result, dict):
            code = result.get("CODE") or "unknown"
            message = result.get("MESSAGE") or ""
            detail = f"{detail} ({code} {message})"
        status_line(False, "SEOUL_CULTURE_API_PROBE", detail)
        return 2

    status_line(True, "SEOUL_CULTURE_API_PROBE", f"{usable_count}/{len(rows)} verified Seongsu popup rows returned")
    return 0


def live_readiness_errors(
    *,
    seoul_key: bool,
    telecom_key: bool,
    telecom_url: bool,
    telecom_template_ok: bool,
    kto_key: bool,
    seoul_culture_key: bool,
    popup_ok: bool,
    public_event_api_records: bool,
) -> list[str]:
    errors: list[str] = []
    if not seoul_key and not (telecom_key and telecom_url and telecom_template_ok):
        errors.append("SEOUL_CITYDATA_API_KEY or complete TELECOM_CROWD_API settings are required for live crowd estimates")
    if not seoul_culture_key and not public_event_api_records:
        errors.append("SEOUL_CULTURE_API_KEY or verified SEOUL_CULTURE_API popup records are required for live Seongsu popup data")
    if not popup_ok:
        errors.append("POPUP_TRENDS_FILE must be valid JSON with a structurally valid items[] array")
    return errors


def check_compose_config() -> bool:
    result = subprocess.run(
        ["docker", "compose", "config"],
        cwd=REPO_ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.stderr.strip():
        print(result.stderr.strip(), file=sys.stderr)
    if result.returncode != 0:
        status_line(False, "docker_compose_config", f"docker compose config failed with {result.returncode}")
        return False

    output = result.stdout
    required_fragments = (
        "api-server:",
        "popup-trend-collector:",
        "SEOUL_CITYDATA_API_KEY:",
        "TELECOM_CROWD_API_KEY:",
        "TELECOM_CROWD_URL_TEMPLATE:",
        "TELECOM_CROWD_API_KEY_HEADER:",
        "KTO_TOUR_API_KEY:",
        "SEOUL_CULTURE_API_KEY:",
        "SEOUL_CULTURE_API_BASE_URL:",
        'SEOUL_CULTURE_API_PAGE_SIZE: "1000"',
        'SEOUL_CULTURE_API_MAX_PAGES: "25"',
        "POPUP_TRENDS_FILE: /app/data/popup-trends.json",
        "POPUP_TRENDS_MAX_AGE_HOURS:",
        "POPUP_TRENDS_REFRESH_SECONDS:",
        "INSIGHTS_CACHE_TTL_SECONDS:",
        'INSIGHTS_EVENT_CACHE_TTL_SECONDS: "86400"',
        "INSIGHTS_CROWD_CURRENT_MAX_AGE_MINUTES:",
        "INSIGHTS_CROWD_LIVE_MAX_AGE_MINUTES:",
        "INSIGHTS_CROWD_ALLOW_MISSING_OBSERVATION_TIME:",
        "INSIGHTS_EVENT_WARMUP_ENABLED:",
        "INSIGHTS_EVENT_WARMUP_INITIAL_DELAY_MS:",
        'INSIGHTS_EVENT_WARMUP_INTERVAL_MS: "86400000"',
        "scripts/run_popup_trend_collector.py",
        "backend/data-scripts/data",
        "target: /app/data",
        "read_only: true",
    )
    missing = [fragment for fragment in required_fragments if fragment not in output]
    if missing:
        status_line(False, "docker_compose_config", f"missing Season 3 fragments: {', '.join(missing)}")
        return False

    status_line(
        True,
        "docker_compose_config",
        "Season 3 provider settings, popup data mount, and scheduled cache settings are present",
    )
    return True


def check_ui_parity() -> bool:
    required_checks = {
        "ios_season3_map_ui": (
            "ios-native/NeogulMapNative/Views/ZoneMapView.swift",
            (
                "UnifiedSearchMenuBar(",
                'TextField("장소, 혼잡, 팝업 검색..."',
                'Image(systemName: "magnifyingglass")',
                'return "흡연구역"',
                'return "실시간 핫플"',
                'return "팝업"',
                "model.eventInsight.events.filter(\\.isVerifiedSeongsuPopup)",
                'keyword: intentKeyword ?? "hot-now"',
                'keyword: "성수 팝업"',
                'UIImage(named: "HotRaccoonMarker")',
            ),
        ),
        "ios_verified_seongsu_popup": (
            "ios-native/NeogulMapNative/Models/SmokingZone.swift",
            (
                "var isVerifiedSeongsuPopup: Bool",
                'title.range(of: "팝업"',
                "MapBounds.seongsu.minLat",
                "Self.parseISODate(startDate)",
                "Self.parseISODate(endDate)",
                "hasAddress",
                "safeSourceURL != nil",
            ),
        ),
        "ios_map_insight_api": (
            "ios-native/NeogulMapNative/Services/NugulAPIClient.swift",
            (
                "func fetchMapInsights",
                'path: "/api/insights/map"',
                "hotplaceLimit",
                "eventLimit",
            ),
        ),
        "android_season3_map_ui": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt",
            (
                "BasicTextField(",
                '"장소·혼잡·팝업 검색"',
                "CompactSearchIcon(",
                'Zones("흡연구역"',
                'Hotplaces("실시간 핫플"',
                'Events("팝업"',
                "widthIn(max = MAP_CATEGORY_SELECTOR_MAX_WIDTH_DP.dp)",
                "hotNowSearchRequest()",
                "seongsuPopupSearchRequest()",
            ),
        ),
        "android_season3_search": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/Season3Search.kt",
            (
                'keyword = "성수 팝업"',
                'keyword = "hot-now"',
                'displayKeyword = "실시간 핫플"',
                "request.displayKeyword",
                "events.filter(TrendEventDto::isVerifiedSeongsuPopup)",
            ),
        ),
        "android_verified_seongsu_popup": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/dto/InsightDto.kt",
            (
                "val isVerifiedSeongsuPopup: Boolean",
                "title.containsExplicitPopupTerm()",
                "MapBounds.seongsu.minLat",
                "hasValidIsoDateRange(startDate, endDate)",
                "hasAddress",
                "hasSafeDetailLink",
            ),
        ),
        "android_hot_raccoon_marker": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt",
            (
                "HOT_RACCOON_MARKER_RESOURCE_NAME",
                '"hot_raccoon_marker"',
                "chooseHotplaceMarkerResource",
            ),
        ),
        "android_map_insight_api": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/api/NugulApiService.kt",
            (
                '@GET("api/insights/map")',
                "suspend fun getMapInsights",
                "hotplaceLimit",
                "eventLimit",
            ),
        ),
        "backend_native_oauth_failure_callback": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/oauth/OAuth2SuccessHandler.java",
            (
                "determineProcessingFailureTargetUrl(request)",
                'queryParam("error", OAUTH2_PROCESSING_FAILED)',
                "authorizationRequestRepository.getClientState(request)",
                "expireAuthenticationCookies(response)",
                "authorizationRequestRepository.removeAuthorizationRequestCookies(request, response)",
            ),
        ),
        "backend_native_oauth_session_ttl": (
            "backend/api-server/src/main/resources/application-prod.yml",
            (
                "session:",
                "timeout: 10m",
            ),
        ),
        "ios_native_oauth_transaction_ttl": (
            "ios-native/NeogulMapNative/Services/NugulAPIClient.swift",
            (
                "validityInterval: TimeInterval = 10 * 60",
                "age <= validityInterval",
            ),
        ),
        "android_native_oauth_transaction_ttl": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/repository/AuthRepository.kt",
            (
                "OAUTH_PENDING_REQUEST_TTL_MILLIS = 10 * 60 * 1000L",
                "ttlMillis = OAUTH_PENDING_REQUEST_TTL_MILLIS",
            ),
        ),
        "backend_cached_hotplaces": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/HotplaceService.java",
            (
                ".map(this::findVerifiedCachedHotplace)",
                '"NO_VERIFIED_DATA"',
                "public void warmHotplaceCache()",
                'case "SEOUL_CITYDATA"',
                'case "TELECOM_CROWD"',
            ),
        ),
        "backend_cached_verified_events": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/EventInsightService.java",
            (
                "readFreshCachedEvents(tourEventsCache)",
                "readFreshCachedEvents(seoulCultureEventsCache)",
                "readPopupTrendEvents()",
                "${SEOUL_CULTURE_API_PAGE_SIZE:1000}",
                "${SEOUL_CULTURE_API_MAX_PAGES:25}",
                "${INSIGHTS_EVENT_CACHE_TTL_SECONDS:86400}",
                "SEOUL_CULTURE_MAX_PAGE_SIZE = 1000",
                "SEOUL_CULTURE_HARD_MAX_PAGES = 50",
                "readSeoulCultureTotalCount(response)",
                "rows.size() != totalCount",
                "new CachedTourEvents(cached.events(), now.plus(lastGoodCacheDuration()))",
                ".filter(this::hasVerifiableEventMetadata)",
                ".filter(this::isPublishableSeongsuPopup)",
                "!isSeongsuPopupIntent(keyword) || isPublishableSeongsuPopup(event)",
                "if (event == null || isManualTrendRecord(item))",
                "safeHttpUrl(event.detailUrl()) == null",
                "!startDate.isAfter(endDate)",
                "isPublicLinkHost(uri.getHost())",
                '"NO_VERIFIED_DATA"',
                "public void warmEventCache()",
            ),
        ),
        "backend_hotplace_warmup": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/HotplaceCacheWarmupScheduler.java",
            (
                "external.insights.hotplace-warmup.enabled",
                "fixedDelayString",
                "hotplaceService.warmHotplaceCache()",
            ),
        ),
        "backend_event_warmup": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/EventCacheWarmupScheduler.java",
            (
                "external.insights.event-warmup.enabled",
                "fixedDelayString",
                "external.insights.event-warmup.interval-ms:86400000",
                "eventInsightService.warmEventCache()",
            ),
        ),
        "collector_verified_publish_gate": (
            "backend/data-scripts/scripts/collect_popup_trends.py",
            (
                "def is_verifiable_record",
                'record.get("collectionMode") == "NETWORK"',
                'normalized_start = normalize_date_text(record.get("startDate"))',
                'normalized_end = normalize_date_text(record.get("endDate"))',
                "start_date is not None",
                "end_date is not None",
                "start_date <= end_date",
                "end_date >= seoul_reference_date(as_of)",
                'safe_http_url(record.get("detailUrl")) is not None',
                'record.get("publicationPolicy") == "allowed_with_attribution"',
                "def is_public_link_host",
            ),
        ),
    }
    forbidden_checks = {
        "ios_primary_ui_copy": (
            "ios-native/NeogulMapNative/Views/ZoneMapView.swift",
            (
                "통신사 URL 필요",
                "서울문화 API 확인",
                "크롤링 팝업 트렌드",
                "STATIC_FALLBACK",
            ),
        ),
        "android_primary_ui_copy": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt",
            (
                "통신사 URL 필요",
                "서울문화 API 확인",
                "크롤링 팝업 트렌드",
                "STATIC_FALLBACK",
            ),
        ),
        "backend_fake_fallbacks": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/EventInsightService.java",
            (
                "FALLBACK_EVENTS",
                "STATIC_EVENT_SEED",
                "STATIC_FALLBACK",
            ),
        ),
        "backend_fake_hotplaces": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/HotplaceService.java",
            (
                "STATIC_SEED",
                "STATIC_FALLBACK",
            ),
        ),
    }
    required_paths = (
        "ios-native/NeogulMapNative/Assets.xcassets/HotRaccoonMarker.imageset/Contents.json",
        "ios-native/NeogulMapNative/Assets.xcassets/HotRaccoonMarker.imageset/hot_raccoon_marker.png",
        "android-native/app/src/main/res/drawable-mdpi/hot_raccoon_marker.png",
        "android-native/app/src/main/res/drawable-xxxhdpi/hot_raccoon_marker.png",
    )

    ok = True
    for label, (path, fragments) in required_checks.items():
        target = REPO_ROOT / path
        if not target.is_file():
            status_line(False, label, f"missing {path}")
            ok = False
            continue
        text = target.read_text(encoding="utf-8")
        missing = [fragment for fragment in fragments if fragment not in text]
        if missing:
            status_line(False, label, f"{path} missing: {', '.join(missing)}")
            ok = False
        else:
            status_line(True, label, f"{path} contains the required Season 3 contract")

    for label, (path, fragments) in forbidden_checks.items():
        target = REPO_ROOT / path
        if not target.is_file():
            status_line(False, label, f"missing {path}")
            ok = False
            continue
        text = target.read_text(encoding="utf-8")
        present = [fragment for fragment in fragments if fragment in text]
        if present:
            status_line(False, label, f"{path} still contains forbidden fragments: {', '.join(present)}")
            ok = False
        else:
            status_line(True, label, f"{path} has no fake fallback or provider-jargon copy")

    for path in required_paths:
        if not (REPO_ROOT / path).is_file():
            status_line(False, "season3_marker_assets", f"missing {path}")
            ok = False
    if all((REPO_ROOT / path).is_file() for path in required_paths):
        status_line(True, "season3_marker_assets", "hot raccoon marker assets exist for iOS and Android")

    config_path = REPO_ROOT / "backend/data-scripts/config/popup_trend_sources.json"
    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        status_line(False, "popup_source_policy", f"invalid {config_path}: {error}")
        return False

    sources = config.get("sources")
    if not isinstance(sources, list):
        status_line(False, "popup_source_policy", "popup source config must contain sources[]")
        return False

    active_sources = [
        source
        for source in sources
        if isinstance(source, dict) and source.get("enabled", True) is not False
    ]
    skt_source = next(
        (
            source
            for source in sources
            if isinstance(source, dict) and source.get("id") == "skt-newsroom-seongsu-discovery"
        ),
        None,
    )
    source_policy_ok = (
        config.get("allowSampleFallback") is False
        and config.get("allowManualFallback") is False
        and len(active_sources) == 1
        and active_sources[0].get("id") == "seoul-culture-seongsu-popups"
        and active_sources[0].get("source") == "SEOUL_CULTURE_API"
        and active_sources[0].get("publicationPolicy") == "allowed_with_attribution"
        and isinstance(skt_source, dict)
        and skt_source.get("enabled") is False
        and bool(str(skt_source.get("disabledReason") or "").strip())
    )
    if source_policy_ok:
        status_line(
            True,
            "popup_source_policy",
            "only the official Seoul Culture Seongsu popup source can auto-publish; manual/sample/SKT paths are disabled",
        )
    else:
        status_line(False, "popup_source_policy", "popup source policy is not strict enough for production")
        ok = False

    return ok



def main() -> int:
    parser = argparse.ArgumentParser(description="Check NugulMap live provider keys, verified popup data, and optional HTTP smoke.")
    parser.add_argument("--base-url", default=None, help="When provided, smoke-test a running API server.")
    parser.add_argument(
        "--popup-file",
        default=os.environ.get("POPUP_TRENDS_FILE", str(DEFAULT_POPUP_FILE)),
        help="Popup trends JSON file, default: POPUP_TRENDS_FILE or repo seed file.",
    )
    parser.add_argument(
        "--require-live",
        action="store_true",
        help="Fail unless one live crowd source and one public event source are ready.",
    )
    parser.add_argument(
        "--strict-popup-quality",
        action="store_true",
        help="Fail when popup trend records miss required fields or have invalid Seoul coordinates.",
    )
    parser.add_argument(
        "--max-popup-age-hours",
        type=int,
        default=positive_env_int("POPUP_TRENDS_MAX_AGE_HOURS", DEFAULT_POPUP_MAX_AGE_HOURS),
        help="Fail when any popup trend record is older than this many hours (default: %(default)s).",
    )
    parser.add_argument(
        "--check-compose",
        action="store_true",
        help="Validate docker compose config contains Season 3 provider, cache, and popup mount settings.",
    )
    parser.add_argument(
        "--check-ui-parity",
        action="store_true",
        help="Validate the iOS, Android, backend, and collector Season 3 native contract.",
    )
    parser.add_argument(
        "--probe-live-provider",
        action="store_true",
        help="Probe the configured live crowd provider directly for Lotte World/Jamsil people range.",
    )
    parser.add_argument(
        "--provider-probe-timeout",
        type=int,
        default=8,
        help="HTTP timeout seconds for --probe-live-provider, default: %(default)s",
    )
    parser.add_argument(
        "--probe-public-event-provider",
        action="store_true",
        help="Probe SEOUL_CULTURE_API_KEY directly for usable Seoul event rows.",
    )
    parser.add_argument(
        "--public-event-probe-timeout",
        type=int,
        default=8,
        help="HTTP timeout seconds for --probe-public-event-provider, default: %(default)s",
    )
    parser.add_argument(
        "--public-event-probe-limit",
        type=int,
        default=100,
        help="Seoul culture rows to request for --probe-public-event-provider, default: %(default)s",
    )
    args = parser.parse_args()

    seoul_key = has_env("SEOUL_CITYDATA_API_KEY")
    telecom_key = has_env("TELECOM_CROWD_API_KEY")
    telecom_url = has_env("TELECOM_CROWD_URL_TEMPLATE")
    telecom_api_key_header = os.environ.get("TELECOM_CROWD_API_KEY_HEADER", "").strip()
    telecom_template_ok, telecom_template_issues = validate_telecom_template(
        os.environ.get("TELECOM_CROWD_URL_TEMPLATE", "").strip(),
        telecom_api_key_header,
    )
    telecom_template_status = (
        "set" if telecom_template_ok else f"issues: {', '.join(telecom_template_issues)}"
    )
    kto_key = has_env("KTO_TOUR_API_KEY")
    seoul_culture_key = has_env("SEOUL_CULTURE_API_KEY")
    popup_file = Path(args.popup_file)
    popup_ok = check_popup_file(
        popup_file,
        strict_quality=args.strict_popup_quality,
        max_age_hours=args.max_popup_age_hours,
    )
    public_event_api_records = has_public_event_api_records(popup_file, args.max_popup_age_hours)
    status_line(seoul_key, "SEOUL_CITYDATA_API_KEY", "set" if seoul_key else "unset; hotplaces will use NO_VERIFIED_DATA")
    status_line(
        telecom_key and telecom_url,
        "TELECOM_CROWD_API",
        "set" if telecom_key and telecom_url else "unset/incomplete; direct telecom crowd adapter will stay disabled",
    )
    status_line(
        telecom_template_ok,
        "TELECOM_CROWD_TEMPLATE",
        telecom_template_status,
    )
    status_line(kto_key, "KTO_TOUR_API_KEY", "set; optional general festival feed" if kto_key else "unset; not used as a Seongsu popup fallback")
    status_line(seoul_culture_key, "SEOUL_CULTURE_API_KEY", "set" if seoul_culture_key else "unset; scheduled Seoul culture refresh will stay disabled")
    status_line(
        seoul_culture_key or public_event_api_records,
        "PUBLIC_EVENT_API_RECORDS",
        "verified rows present" if public_event_api_records else "no verified rows in the current popup file",
    )

    smoke_code = 0
    if args.base_url:
        smoke_code = run_smoke(args.base_url, args.require_live)

    provider_probe_code = 0
    if args.probe_live_provider:
        provider_probe_code = run_provider_probe(args.provider_probe_timeout)

    public_event_probe_code = 0
    if args.probe_public_event_provider:
        public_event_probe_code = run_public_event_provider_probe(
            args.public_event_probe_timeout,
            args.public_event_probe_limit,
        )

    compose_ok = True
    if args.check_compose:
        compose_ok = check_compose_config()

    ui_parity_ok = True
    if args.check_ui_parity:
        ui_parity_ok = check_ui_parity()

    if args.require_live:
        errors = live_readiness_errors(
            seoul_key=seoul_key,
            telecom_key=telecom_key,
            telecom_url=telecom_url,
            telecom_template_ok=telecom_template_ok,
            kto_key=kto_key,
            seoul_culture_key=seoul_culture_key,
            popup_ok=popup_ok,
            public_event_api_records=public_event_api_records,
        )
        if errors:
            for error in errors:
                status_line(False, "require_live", error)
            return 2
    if smoke_code != 0:
        return smoke_code
    if provider_probe_code != 0:
        return provider_probe_code
    if public_event_probe_code != 0:
        return public_event_probe_code
    return 0 if popup_ok and compose_ok and ui_parity_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
