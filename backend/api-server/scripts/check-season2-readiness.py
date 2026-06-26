#!/usr/bin/env python3
"""Check local readiness for NugulMap Season 2 hotplace/event insights."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import re
import urllib.parse
import urllib.request
from datetime import datetime, timezone
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


def status_line(ok: bool, label: str, detail: str) -> None:
    prefix = "OK" if ok else "WARN"
    print(f"{prefix} {label}: {detail}")


def has_env(name: str) -> bool:
    return bool(os.environ.get(name, "").strip())


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


def is_in_seoul_bounds(item: dict[str, Any]) -> bool:
    try:
        latitude = float(item.get("latitude"))
        longitude = float(item.get("longitude"))
    except (TypeError, ValueError):
        return False
    return (
        SEOUL_BOUNDS["min_lat"] <= latitude <= SEOUL_BOUNDS["max_lat"]
        and SEOUL_BOUNDS["min_lng"] <= longitude <= SEOUL_BOUNDS["max_lng"]
    )


def popup_quality_errors(items: list[Any], max_age_hours: int | None) -> list[str]:
    errors: list[str] = []
    now = datetime.now(timezone.utc)

    for index, item in enumerate(items):
        if not isinstance(item, dict):
            errors.append(f"item[{index}] must be an object")
            continue

        for field in ("id", "title", "kind", "latitude", "longitude", "source"):
            if item.get(field) in (None, ""):
                errors.append(f"item[{index}] missing {field}")

        if not is_in_seoul_bounds(item):
            errors.append(f"item[{index}] outside Seoul bounds or has invalid coordinates")

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
    if not isinstance(items, list) or not items:
        status_line(False, "popup_trends_file", "JSON must contain non-empty items[]")
        return False

    errors = popup_quality_errors(items, max_age_hours)
    if errors:
        detail = "; ".join(errors[:5])
        if len(errors) > 5:
            detail += f"; +{len(errors) - 5} more"
        status_line(False, "popup_trends_quality", detail)
        if strict_quality or max_age_hours is not None:
            return False
    else:
        status_line(True, "popup_trends_quality", "required fields, Seoul bounds, and freshness checks passed")

    status_line(True, "popup_trends_file", f"{len(items)} items at {path}")
    return True


def has_public_event_api_records(path: Path) -> bool:
    if not path.exists():
        return False

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False

    items = parse_items(payload)
    if not isinstance(items, list):
        return False

    return any(
        isinstance(item, dict)
        and item.get("source") in {"SEOUL_CULTURE_API", "KTO_TOUR_API"}
        and is_in_seoul_bounds(item)
        for item in items
    )


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
    title = item.get("TITLE") or item.get("title")
    latitude = item.get("LAT") or item.get("latitude")
    longitude = item.get("LOT") or item.get("longitude")
    return bool(title) and is_in_seoul_bounds({"latitude": latitude, "longitude": longitude})


def run_public_event_provider_probe(timeout: int, limit: int) -> int:
    api_key = os.environ.get("SEOUL_CULTURE_API_KEY", "").strip()
    if not api_key:
        status_line(False, "SEOUL_CULTURE_API_PROBE", "SEOUL_CULTURE_API_KEY is not configured")
        return 2

    normalized_limit = max(1, min(limit, 50))
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
        detail = "no usable Seoul event rows with title and coordinates"
        if isinstance(result, dict):
            code = result.get("CODE") or "unknown"
            message = result.get("MESSAGE") or ""
            detail = f"{detail} ({code} {message})"
        status_line(False, "SEOUL_CULTURE_API_PROBE", detail)
        return 2

    status_line(True, "SEOUL_CULTURE_API_PROBE", f"{usable_count}/{len(rows)} usable Seoul event rows returned")
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
    if not kto_key and not seoul_culture_key and not public_event_api_records:
        errors.append("KTO_TOUR_API_KEY, SEOUL_CULTURE_API_KEY, or SEOUL_CULTURE_API popup trend records are required for live public event data")
    if not popup_ok:
        errors.append("POPUP_TRENDS_FILE must contain valid crawled popup/event records")
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
        "SEOUL_CULTURE_API_END_INDEX:",
        "POPUP_TRENDS_FILE: /app/data/popup-trends.json",
        "POPUP_TRENDS_REFRESH_SECONDS:",
        "INSIGHTS_CACHE_TTL_SECONDS:",
        "scripts/run_popup_trend_collector.py",
        "backend/data-scripts/data",
        "target: /app/data",
        "read_only: true",
    )
    missing = [fragment for fragment in required_fragments if fragment not in output]
    if missing:
        status_line(False, "docker_compose_config", f"missing Season 2 fragments: {', '.join(missing)}")
        return False

    status_line(True, "docker_compose_config", "Season 2 public, telecom, event API keys, popup data mount, and cache TTL are present")
    return True


def file_contains(path: str, *fragments: str) -> list[str]:
    text = (REPO_ROOT / path).read_text(encoding="utf-8")
    return [fragment for fragment in fragments if fragment not in text]


def check_ui_parity() -> bool:
    checks = {
        "web_shortcuts": (
            "frontend/app/page.tsx",
            (
                "롯데월드 혼잡도",
                "지금 핫한 곳",
                "hot-now",
                "성수 팝업",
                "fetchMapInsights(5, 5, undefined, query)",
                "focusHotplace(first)",
                "focusEvent(first)",
            ),
        ),
        "web_local_env_example": (
            "frontend/.env.example",
            (
                "NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080",
                "NEXT_PUBLIC_API_BASE_URL=https://api.nugulmap.com",
                "MOCK_CROWD=1 REQUIRE_LIVE=1",
                "NEXT_PUBLIC_KAKAOMAP_APIKEY=",
            ),
        ),
        "web_development_season2_local": (
            "frontend/DEVELOPMENT.md",
            (
                "로컬 Season 2 API 검증",
                "cp .env.example .env.local",
                "NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080",
                "롯데월드 사람 많아?",
                "요즘 핫한 팝업 행사",
            ),
        ),
        "web_map_layers": (
            "frontend/components/map-container.tsx",
            (
                'type Season2LayerMode = "all" | "zones" | "hotplaces" | "events"',
                "fetchMapInsights",
                "focusHotplace: (place: Hotplace) => void",
                "focusEvent: (event: TrendEvent) => void",
                'setLayerMode("hotplaces")',
                'setLayerMode("events")',
                "formatTelecomStatus",
                "통신사 URL 필요",
                "formatHotplacePanelStatus",
                "formatEventPanelStatus",
                "CRAWLED_OR_PARTIAL",
                "서울 문화행사 API",
                "formatCrowdLabel",
                "estimatedMinPeople.toLocaleString()",
                "estimatedMaxPeople.toLocaleString()",
                "mergeFocusedHotplaceInsight",
            ),
        ),
        "expo_map_people_range": (
            "mobile/src/screens/MapScreen.tsx",
            (
                "formatHotplaceOverlayLabel",
                "estimatedMinPeople",
                "estimatedMaxPeople",
                "formatPeopleRange",
                "formatPeopleCount",
            ),
        ),
        "expo_status_contract": (
            "mobile/src/services/nugulApi.ts",
            (
                "telecomCrowdKeyConfigured",
                "telecomCrowdUrlTemplateConfigured",
                "seoulCultureApiKeyConfigured",
                "seoulCultureApi",
                "popupTrends",
            ),
        ),
        "expo_map_insight_contract": (
            "mobile/src/services/nugulApi.ts",
            (
                "export async function fetchMapInsights",
                "/api/insights/map",
                "hotplaceLimit",
                "eventLimit",
                "pickMapInsight",
            ),
        ),
        "expo_status_ui": (
            "mobile/App.tsx",
            (
                "fetchMapInsights",
                "formatInsightStatus",
                "formatTelecomStatus",
                "formatHotplacePanelStatus",
                "formatEventPanelStatus",
                "통신사 URL 필요",
                "서울문화 API 확인",
                "CRAWLED_OR_PARTIAL",
                "크롤링 팝업 트렌드",
                "서울 문화행사 API",
                "fetchMapInsights(query, 5, 5",
                "focusEventOnMap(mapInsight.events.events[0])",
            ),
        ),
        "ios_shortcuts": (
            "ios-native/NeogulMapNative/Views/ZoneMapView.swift",
            (
                "Season2QuickActionStrip",
                "Season2Shortcut",
                'return "롯데월드 혼잡도"',
                'return "지금 핫한 곳"',
                'return "hot-now"',
                'return "성수 팝업"',
                "layerMode = shortcut.layerMode",
                "model.search()",
                "hotplaceDetail(_ place: Hotplace)",
                "eventDetail(_ event: TrendEvent)",
            ),
        ),
        "ios_source_labels": (
            "ios-native/NeogulMapNative/Models/SmokingZone.swift",
            (
                "var sourceLabel: String",
                '"TELECOM_CROWD"',
                '"통신사 장소 혼잡도"',
                '"CRAWLED_POPUP_TREND"',
                '"크롤링 팝업 트렌드"',
                '"SEOUL_CULTURE_API"',
                '"서울 문화행사 API"',
                "struct InsightStatus",
                "telecomCrowdUrlTemplateConfigured",
                "seoulCultureApiKeyConfigured",
                "seoulCultureApi",
                "popupTrends",
                "var compactMapLabel: String",
                "estimatedMinPeople",
                "estimatedMaxPeople",
                "compactPeopleRange",
            ),
        ),
        "ios_status_api": (
            "ios-native/NeogulMapNative/Services/NugulAPIClient.swift",
            (
                "func fetchMapInsights",
                "/api/insights/map",
            ),
        ),
        "ios_map_insight_api": (
            "ios-native/NeogulMapNative/Services/NugulAPIClient.swift",
            (
                "func fetchMapInsights",
                "/api/insights/map",
                "hotplaceLimit",
                "eventLimit",
            ),
        ),
        "ios_status_state": (
            "ios-native/NeogulMapNative/ViewModels/ZoneExplorerModel.swift",
            (
                "insightStatus",
                "fetchMapInsights",
                "insight.events",
                "insight.hotplaces",
            ),
        ),
        "ios_status_ui": (
            "ios-native/NeogulMapNative/Views/ZoneMapView.swift",
            (
                "formatInsightStatus",
                "formatTelecomStatus",
                "formatHotplacePanelStatus",
                "formatEventPanelStatus",
                "통신사 URL 필요",
                "서울문화 API 확인",
                "CRAWLED_OR_PARTIAL",
                "hotplaceSubtitle(_ place: Hotplace)",
                "place.compactMapLabel",
            ),
        ),
        "android_shortcuts": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt",
            (
                "Season2QuickActionStrip",
                "Season2Shortcut",
                'LotteWorldCrowd("롯데월드 혼잡도"',
                'HotNow("지금 핫한 곳", "hot-now"',
                'SeongsuPopup("성수 팝업", "성수"',
                "layerMode = shortcut.targetLayerMode",
                "viewModel.runSeason2Shortcut(shortcut)",
                '"TELECOM_CROWD" -> "통신사 장소 혼잡도"',
                '"CRAWLED_POPUP_TREND" -> "크롤링 팝업 트렌드"',
                '"SEOUL_CULTURE_API" -> "서울 문화행사 API"',
                "formatInsightStatus",
                "formatTelecomStatus",
                "formatHotplacePanelStatus",
                "formatEventPanelStatus",
                "통신사 URL 필요",
                "서울문화 API 확인",
                "CRAWLED_OR_PARTIAL",
            ),
        ),
        "android_map_people_range": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt",
            (
                "formatHotplaceMapLabel",
                "estimatedMinPeople",
                "estimatedMaxPeople",
                "formatCompactPeopleRange",
                "LabelTextBuilder().setTexts(formatHotplaceMapLabel(place))",
            ),
        ),
        "android_status_contract": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/dto/InsightDto.kt",
            (
                "data class InsightStatusPayload",
                "telecomCrowdKeyConfigured",
                "telecomCrowdUrlTemplateConfigured",
                "seoulCultureApiKeyConfigured",
                "seoulCultureApi",
                "popupTrends",
            ),
        ),
        "android_map_insight_contract": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/dto/InsightDto.kt",
            (
                "data class MapInsightPayload",
                "val hotplaces: HotplaceInsightPayload",
                "val events: EventInsightPayload",
                "val status: InsightStatusPayload?",
            ),
        ),
        "android_status_api": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/api/NugulApiService.kt",
            (
                "api/insights/map",
                "getMapInsights",
            ),
        ),
        "android_map_insight_api": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/api/NugulApiService.kt",
            (
                "api/insights/map",
                "getMapInsights",
                "hotplaceLimit",
                "eventLimit",
            ),
        ),
        "android_repository_keyword_search": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/data/repository/ZoneRepository.kt",
            (
                "loadMapInsights",
                "keyword?.trim()?.takeIf",
                "hotplaceLimit",
                "eventLimit",
            ),
        ),
        "android_status_state": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapUiState.kt",
            (
                "InsightStatusPayload",
                "insightStatus",
            ),
        ),
        "android_status_view_model": (
            "android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapViewModel.kt",
            (
                "loadMapInsights",
                "insightStatus = status",
                "InsightStatusPayload",
            ),
        ),
        "web_map_insight_contract": (
            "frontend/lib/api.ts",
            (
                "export interface MapInsight",
                "export async function fetchMapInsights",
                "/api/insights/map",
                "hotplaceLimit",
                "eventLimit",
            ),
        ),
        "backend_status_contract": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/dto/InsightStatusResponse.java",
            (
                "boolean seoulCityDataKeyConfigured",
                "boolean telecomCrowdKeyConfigured",
                "boolean telecomCrowdUrlTemplateConfigured",
                "boolean ktoTourApiKeyConfigured",
                "boolean seoulCultureApiKeyConfigured",
                "ProviderStatus telecomCrowd",
                "ProviderStatus seoulCultureApi",
                "PopupTrendStatus popupTrends",
            ),
        ),
        "backend_map_insight_contract": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/dto/MapInsightResponse.java",
            (
                "HotplaceResponse hotplaces",
                "EventInsightResponse events",
                "InsightStatusResponse status",
                "Instant updatedAt",
            ),
        ),
        "backend_map_insight_controller": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/controller/InsightController.java",
            (
                '@GetMapping("/map")',
                "hotplaceLimit",
                "eventLimit",
                "getHotplaces(keyword, hotplaceLimit",
                "getEvents(keyword, eventLimit",
                "insightStatusService.getStatus()",
            ),
        ),
        "backend_status_service": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/InsightStatusService.java",
            (
                "TELECOM_CROWD_API_KEY",
                "TELECOM_CROWD_URL_TEMPLATE",
                "SEOUL_CULTURE_API_KEY",
                "hasTelecomUrlTemplate",
                "hasTelecomUrlTemplate,",
                "inspectPopupTrends()",
            ),
        ),
        "backend_seoul_culture_runtime_provider": (
            "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/EventInsightService.java",
            (
                "SEOUL_CULTURE_API_KEY",
                "SEOUL_CULTURE_API_BASE_URL",
                "fetchSeoulCultureEvents",
                "오늘",
                "가볼만한",
                "culturalEventInfo",
                "toSeoulCultureEvent",
                "SEOUL_CULTURE_API",
            ),
        ),
        "season2_env_sample": (
            "backend/api-server/.env.sample",
            (
                "SEOUL_CITYDATA_API_KEY, or",
                "TELECOM_CROWD_API_KEY + TELECOM_CROWD_URL_TEMPLATE",
                "SEOUL_CULTURE_API_KEY for runtime Seoul culture API lookup",
                "public event API records collected in POPUP_TRENDS_FILE",
                "SEOUL_CULTURE_API_BASE_URL=http://openapi.seoul.go.kr:8088",
            ),
        ),
        "season2_runbook_real_live": (
            "docs/season2-live-insights-runbook.txt",
            (
                "export SEOUL_CULTURE_API_KEY",
                "export KTO_TOUR_API_KEY",
                "npm run lint",
                "cp .env.example .env.local",
                "NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080",
                "--probe-public-event-provider",
                "The goal is complete only when the real provider run",
            ),
        ),
        "season2_product_plan": (
            "docs/season2-product-plan.md",
            (
                "롯데월드 사람 많아?",
                "지금 핫한 곳",
                "요즘 핫한 팝업 행사",
                "요즘 뜨는 장소 어디",
                "TELECOM_CROWD_API_KEY",
                "SEOUL_CITYDATA_API_KEY",
                "SEOUL_CULTURE_API_KEY",
                "KTO_TOUR_API_KEY",
                "POPUP_TRENDS_FILE",
                "GET /api/insights/map",
                "--probe-live-provider",
                "--probe-public-event-provider",
                "strict live gate passes",
            ),
        ),
        "backend_smoke_scenarios": (
            "backend/api-server/scripts/smoke-season2-insights.py",
            (
                "def smoke_hotplaces",
                "def smoke_hot_now",
                "def smoke_seongsu_popup",
                "def smoke_generic_event_discovery",
                "def smoke_insight_map",
                "/api/insights/map",
                "요즘 핫한 팝업 행사",
                "telecomCrowdKeyConfigured",
                "telecomCrowdUrlTemplateConfigured",
                "seoulCultureApiKeyConfigured",
                "seoulCultureApi",
                "estimatedMinPeople and estimatedMaxPeople",
            ),
        ),
    }

    ok = True
    for label, (path, fragments) in checks.items():
        missing = file_contains(path, *fragments)
        if missing:
            status_line(False, label, f"{path} missing: {', '.join(missing)}")
            ok = False
        else:
            status_line(True, label, f"{path} contains required Season 2 contract fragments")

    return ok


def main() -> int:
    parser = argparse.ArgumentParser(description="Check Season 2 API keys, crawled data, and optional HTTP smoke.")
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
        default=None,
        help="Fail when any popup trend record is older than this many hours.",
    )
    parser.add_argument(
        "--check-compose",
        action="store_true",
        help="Validate docker compose config contains Season 2 env vars and popup data mount.",
    )
    parser.add_argument(
        "--check-ui-parity",
        action="store_true",
        help="Validate web, iOS, and Android Season 2 quick actions and layer controls are wired.",
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
        default=10,
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
    public_event_api_records = has_public_event_api_records(popup_file)
    status_line(seoul_key, "SEOUL_CITYDATA_API_KEY", "set" if seoul_key else "unset; hotplaces will use STATIC_FALLBACK")
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
    status_line(kto_key, "KTO_TOUR_API_KEY", "set" if kto_key else "unset; events depend on crawled/fallback data")
    status_line(seoul_culture_key, "SEOUL_CULTURE_API_KEY", "set" if seoul_culture_key else "unset; runtime Seoul culture API adapter will stay disabled")
    status_line(
        seoul_culture_key or public_event_api_records,
        "PUBLIC_EVENT_API_RECORDS",
        "set" if public_event_api_records else "missing; event live gate can also use SEOUL_CULTURE_API_KEY",
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
