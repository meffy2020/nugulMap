#!/usr/bin/env python3
"""Verify the public web/API surfaces required by both mobile stores."""

from __future__ import annotations

import argparse
import html
import ipaddress
import json
import math
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Any
from pathlib import Path
from zoneinfo import ZoneInfo


# Exact known development fixtures only; real/edited records must not be guessed as demos.
DEMO_SIGNATURES_PATH = Path(__file__).resolve().parents[1] / "backend/api-server/src/main/resources/db/manual/known-demo-zones.json"
KNOWN_DEMO_ZONES = json.loads(DEMO_SIGNATURES_PATH.read_text(encoding="utf-8"))


def is_known_demo_zone(zone: dict[str, Any]) -> bool:
    return any(all(zone.get(field) == signature[field] for field in (
        "address", "latitude", "longitude", "description"
    )) for signature in KNOWN_DEMO_ZONES)


EMAIL_PATTERN = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")
PAGE_REQUIRED_CONTENT = {
    "/privacy": "개인정보 처리방침",
    "/account-deletion": "계정 삭제 안내",
    "/connected-accounts": "로그인 연결 관리",
    "/support": "지원 및 콘텐츠 신고",
    "/terms": "서비스 이용약관",
}
SEONGSU_LATITUDE_RANGE = (37.532, 37.558)
SEONGSU_LONGITUDE_RANGE = (127.032, 127.072)
SEONGSU_TERMS = ("성수", "서울숲", "연무장")
TOBACCO_TERMS = ("담배", "니코틴", "베이프", "궐련", "아이코스", "tobacco", "nicotine", "iqos", "juul", "vape", "cigarette")
APPROVED_POPUP_SOURCE = "SEOUL_CULTURE_API"
APPROVED_POPUP_ATTRIBUTION = "서울특별시 문화행사 정보"
APPROVED_POPUP_SOURCE_URL = "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do"
APPROVED_POPUP_LICENSE = "공공누리 제1유형"
APPROVED_POPUP_LICENSE_URL = "https://www.kogl.or.kr/info/licenseType1.do"
PROVIDER_STATUS_MAX_AGE = timedelta(hours=24)


@dataclass(frozen=True)
class Result:
    status: str
    name: str
    detail: str


def request(url: str, timeout: float) -> tuple[int, str, bytes]:
    headers = {
        "Accept": "application/json,text/html;q=0.9,*/*;q=0.8",
        "User-Agent": "NugulMapReleaseGate/1.0",
    }
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.headers.get("Content-Type", ""), exc.read()


def check_page(base_url: str, path: str, timeout: float) -> Result:
    url = f"{base_url.rstrip('/')}{path}"
    try:
        status, content_type, body = request(url, timeout)
    except (OSError, urllib.error.URLError) as exc:
        return Result("FAIL", path, f"request failed: {exc}")
    if status != 200:
        return Result("FAIL", path, f"HTTP {status}: {url}")
    if "text/html" not in content_type.lower() or len(body.strip()) < 100:
        return Result("FAIL", path, f"unexpected response ({content_type}, {len(body)} bytes)")
    required_content = PAGE_REQUIRED_CONTENT.get(path)
    decoded_body = html.unescape(body.decode("utf-8", errors="replace"))
    if required_content and required_content not in decoded_body:
        return Result("FAIL", path, f"required content is missing: {required_content}")
    return Result("PASS", path, f"HTTP 200 ({len(body)} bytes)")


def parse_json(url: str, timeout: float) -> tuple[Result, Any | None]:
    try:
        status, content_type, body = request(url, timeout)
    except (OSError, urllib.error.URLError) as exc:
        return Result("FAIL", url, f"request failed: {exc}"), None
    if status != 200:
        return Result("FAIL", url, f"HTTP {status}"), None
    try:
        payload = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        return Result("FAIL", url, f"invalid JSON ({content_type}): {exc}"), None
    return Result("PASS", url, "HTTP 200 JSON"), payload


def iter_values(value: Any, path: str = "$"):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            yield child_path, key, child
            yield from iter_values(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_values(child, f"{path}[{index}]")


def check_zone_privacy(payload: Any) -> Result:
    failure, data = envelope_data(payload, "public-zones-contract")
    if failure is not None or data is None:
        return failure or Result("FAIL", "public-zones-contract", "data object is required")
    if not isinstance(data.get("zones"), list):
        return Result("FAIL", "public-zones-contract", "data.zones list is required")

    leaks: list[str] = []
    for path, key, value in iter_values(payload):
        if key.lower() not in {"user", "email", "creatoremail", "author_email", "authoremail"}:
            continue
        if isinstance(value, str) and EMAIL_PATTERN.match(value.strip()):
            leaks.append(path)
            if len(leaks) == 5:
                break
    if leaks:
        return Result("FAIL", "zone-public-pii", f"email-like values exposed at {', '.join(leaks)}")
    return Result("PASS", "zone-public-pii", "no email-like values in public zone response")


def check_zone_records(payload: Any) -> Result:
    failure, data = envelope_data(payload, "smoking-zone-data")
    if failure is not None:
        return failure
    zones = data.get("zones")
    if not isinstance(zones, list) or not zones:
        return Result("FAIL", "smoking-zone-data", "launch-area query must return usable smoking zones")
    for index, zone in enumerate(zones):
        if not isinstance(zone, dict):
            return Result("FAIL", "smoking-zone-data", f"zones[{index}] must be an object")
        if type(zone.get("id")) is not int or zone["id"] <= 0:
            return Result("FAIL", "smoking-zone-data", f"zones[{index}] has no valid detail id")
        for field, limit in (("latitude", 90), ("longitude", 180)):
            value = zone.get(field)
            if type(value) not in (int, float) or not math.isfinite(value) or not -limit <= value <= limit:
                return Result("FAIL", "smoking-zone-data", f"zones[{index}] has invalid {field}")
        if not isinstance(zone.get("address"), str) or not zone["address"].strip():
            return Result("FAIL", "smoking-zone-data", f"zones[{index}] has no address")
        if zone["address"].strip() in {"실내", "실외"}:
            return Result("FAIL", "smoking-zone-data", f"zones[{index}] has a category in place of an address")
        if is_known_demo_zone(zone):
            return Result("FAIL", "smoking-zone-data", f"zones[{index}] id={zone['id']} exactly matches a known demo fixture; quarantine and verify before launch")
        if zone.get("publicationStatus") not in (None, "PUBLISHED"):
            return Result("FAIL", "smoking-zone-data", f"zones[{index}] is not published")
    return Result("PASS", "smoking-zone-data", f"{len(zones)} zones have detail ids, coordinates and addresses; on-site verification still required")


def check_discovery_disabled(api_base: str, path: str, timeout: float) -> Result:
    """Probe retired public reads; authentication failures do not prove route removal."""
    name = "smoking-only-scope:" + path.split("?")[0]
    try:
        status, _, _ = request(f"{api_base.rstrip('/')}{path}", timeout)
    except (OSError, urllib.error.URLError) as exc:
        return Result("FAIL", name, f"unable to verify disabled route: {exc}")
    if status in (404, 410):
        return Result("PASS", name, f"excluded route is unavailable (HTTP {status})")
    return Result("FAIL", name, f"expected disabled route (404/410), got HTTP {status}; deploy the smoking-only backend")


def envelope_data(payload: Any, name: str) -> tuple[Result | None, dict[str, Any] | None]:
    if not isinstance(payload, dict) or payload.get("success") is not True:
        return Result("FAIL", name, "success=true envelope is required"), None
    data = payload.get("data")
    if not isinstance(data, dict):
        return Result("FAIL", name, "data object is required"), None
    return None, data


def check_moderation_contract(payload: Any) -> Result:
    failure, data = envelope_data(payload, "public-moderation-contract")
    if failure is not None or data is None:
        return failure or Result("FAIL", "public-moderation-contract", "data object is required")

    if data.get("supportEmailConfigured") is not True:
        return Result(
            "FAIL",
            "public-moderation-contract",
            "supportEmailConfigured=true is required for a staffed public contact",
        )

    expected_paths = {
        "supportRequestPath": "/api/public/support/requests",
        "reportPathTemplate": "/api/zones/{zoneId}/reviews/{reviewId}/reports",
        "blockPathTemplate": "/api/users/{userId}/block",
    }
    for key, expected in expected_paths.items():
        if data.get(key) != expected:
            return Result("FAIL", "public-moderation-contract", f"{key} must be {expected}")

    categories = data.get("supportCategories")
    if not isinstance(categories, list) or not {"ACCOUNT_DELETION", "CONTENT_REPORT"}.issubset(categories):
        return Result(
            "FAIL",
            "public-moderation-contract",
            "supportCategories must include ACCOUNT_DELETION and CONTENT_REPORT",
        )
    reasons = data.get("reportReasons")
    if not isinstance(reasons, list) or not reasons or not all(isinstance(reason, str) and reason for reason in reasons):
        return Result("FAIL", "public-moderation-contract", "reportReasons must be a non-empty string list")
    return Result(
        "PASS",
        "public-moderation-contract",
        "staffed support contact plus support, report, and block paths are published",
    )


def parse_event_date(value: Any) -> date | None:
    if not isinstance(value, str) or not value.strip():
        return None
    normalized = value.strip()
    for format_string in ("%Y-%m-%d", "%Y%m%d", "%Y.%m.%d"):
        try:
            return datetime.strptime(normalized, format_string).date()
        except ValueError:
            continue
    return None


def check_health_contract(payload: Any) -> Result:
    if not isinstance(payload, dict) or payload.get("status") != "UP":
        return Result("FAIL", "api-health", "status=UP object is required")
    return Result("PASS", "api-health", "status is UP")


def parse_legacy_ipv4(hostname: str) -> ipaddress.IPv4Address | None:
    """Parse browser-compatible decimal, octal, and hexadecimal IPv4 forms."""
    parts = hostname.split(".")
    if not 1 <= len(parts) <= 4 or any(not part for part in parts):
        return None

    numbers: list[int] = []
    for part in parts:
        normalized = part.lower()
        if normalized.startswith("0x"):
            digits = normalized[2:]
            base = 16
            pattern = r"[0-9a-f]+"
        elif len(normalized) > 1 and normalized.startswith("0"):
            digits = normalized[1:]
            base = 8
            pattern = r"[0-7]*"
        else:
            digits = normalized
            base = 10
            pattern = r"[0-9]+"
        if re.fullmatch(pattern, digits) is None:
            return None
        try:
            numbers.append(int(digits or "0", base))
        except ValueError:
            return None

    if any(number > 255 for number in numbers[:-1]):
        return None
    last_bits = 8 * (5 - len(numbers))
    if numbers[-1] >= 1 << last_bits:
        return None

    value = numbers[-1]
    for index, number in enumerate(numbers[:-1]):
        value |= number << (24 - (8 * index))
    return ipaddress.IPv4Address(value)


def is_public_http_url(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    parsed = urllib.parse.urlparse(value.strip())
    try:
        hostname_value = parsed.hostname
    except ValueError:
        return False
    if parsed.scheme not in {"http", "https"} or not hostname_value:
        return False
    if parsed.username is not None or parsed.password is not None:
        return False

    hostname = hostname_value.rstrip(".").lower()
    if hostname == "localhost" or hostname.endswith(".localhost"):
        return False
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        address = parse_legacy_ipv4(hostname)
        if address is None:
            return True
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped is not None:
        return False
    return address.is_global


def is_exact_public_url_on_host(value: Any, expected_url: str, expected_host: str) -> bool:
    if value != expected_url or not is_public_http_url(value):
        return False
    try:
        hostname = urllib.parse.urlparse(value).hostname
    except ValueError:
        return False
    if not hostname:
        return False
    normalized_host = hostname.rstrip(".").lower()
    return normalized_host == expected_host or normalized_host.endswith(f".{expected_host}")


def popup_validation_error(item: Any, today: date) -> str | None:
    if not isinstance(item, dict):
        return "must be an object"

    title = str(item.get("title") or "").strip()
    address = str(item.get("address") or "").strip()
    location = item.get("location")
    if isinstance(location, dict):
        location_text = " ".join(
            str(location.get(key) or "")
            for key in ("name", "address", "placeName")
        )
    else:
        location_text = str(location or "")
    seongsu_searchable = " ".join((title, address, location_text)).lower()
    safety_searchable = " ".join(
        str(item.get(key) or "")
        for key in (
            "title",
            "address",
            "location",
            "kind",
            "period",
            "source",
            "sourceUrl",
            "attribution",
            "license",
            "licenseUrl",
            "detailUrl",
            "sourceContentId",
        )
    ).lower()
    if not title or not any(term in title.lower() for term in ("팝업", "popup")):
        return "title must explicitly identify a popup"
    if not any(term in seongsu_searchable for term in SEONGSU_TERMS):
        return "title, address, or location must identify Seongsu"
    if any(term in safety_searchable for term in TOBACCO_TERMS):
        return "tobacco or nicotine popup is not allowed"

    try:
        latitude = float(item.get("latitude"))
        longitude = float(item.get("longitude"))
    except (TypeError, ValueError):
        return "finite coordinates are required"
    if not (SEONGSU_LATITUDE_RANGE[0] <= latitude <= SEONGSU_LATITUDE_RANGE[1]):
        return "latitude is outside the Seongsu launch bounds"
    if not (SEONGSU_LONGITUDE_RANGE[0] <= longitude <= SEONGSU_LONGITUDE_RANGE[1]):
        return "longitude is outside the Seongsu launch bounds"

    start_date = parse_event_date(item.get("startDate"))
    end_date = parse_event_date(item.get("endDate"))
    if start_date is None or end_date is None or start_date > end_date:
        return "valid startDate and endDate are required"
    if end_date < today:
        return "popup has already ended"
    if not is_public_http_url(item.get("detailUrl")):
        return "public detailUrl is required"
    source = str(item.get("source") or "").strip()
    if source != APPROVED_POPUP_SOURCE:
        return f"source must be {APPROVED_POPUP_SOURCE}"
    attribution = str(item.get("attribution") or "").strip()
    if attribution != APPROVED_POPUP_ATTRIBUTION:
        return f"attribution must be {APPROVED_POPUP_ATTRIBUTION}"
    if not is_exact_public_url_on_host(
        item.get("sourceUrl"),
        APPROVED_POPUP_SOURCE_URL,
        "data.seoul.go.kr",
    ):
        return f"sourceUrl must be {APPROVED_POPUP_SOURCE_URL}"
    license_name = str(item.get("license") or "").strip()
    if license_name != APPROVED_POPUP_LICENSE:
        return f"license must be {APPROVED_POPUP_LICENSE}"
    if not is_exact_public_url_on_host(
        item.get("licenseUrl"),
        APPROVED_POPUP_LICENSE_URL,
        "kogl.or.kr",
    ):
        return f"licenseUrl must be {APPROVED_POPUP_LICENSE_URL}"
    return None


def parse_provider_instant(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    normalized = value.strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return None
    return parsed.astimezone(timezone.utc)


def empty_event_provider_contract(status: Any, *, now: datetime | None = None) -> Result:
    if not isinstance(status, dict):
        return Result("FAIL", "seongsu-popup-contract", "data.status object is required for an empty event result")

    popup_status = status.get("popupTrends")
    if isinstance(popup_status, dict) and popup_status.get("fileConfigured") is True:
        popup_quality = popup_status.get("qualityStatus")
        if popup_quality in {"MISSING_FILE", "INVALID_JSON", "STALE"}:
            return Result("FAIL", "seongsu-popup-contract", f"popupTrends is unhealthy: {popup_quality}")

    reference_time = now or datetime.now(timezone.utc)
    healthy_providers: list[str] = []
    configured_providers: list[str] = []
    for provider_name in ("ktoTourApi", "seoulCultureApi"):
        provider = status.get(provider_name)
        if not isinstance(provider, dict) or provider.get("configured") is not True:
            continue
        configured_providers.append(provider_name)
        quality_status = provider.get("qualityStatus")
        if quality_status == "ERROR":
            return Result("FAIL", "seongsu-popup-contract", f"{provider_name} reports provider error")
        if quality_status != "OK":
            continue
        last_success_at = parse_provider_instant(provider.get("lastSuccessAt"))
        if last_success_at is None:
            return Result("FAIL", "seongsu-popup-contract", f"{provider_name} has no valid lastSuccessAt")
        age = reference_time.astimezone(timezone.utc) - last_success_at
        if age < -timedelta(minutes=5) or age > PROVIDER_STATUS_MAX_AGE:
            return Result("FAIL", "seongsu-popup-contract", f"{provider_name} success status is stale or future-dated")
        healthy_providers.append(provider_name)

    if not configured_providers:
        return Result("FAIL", "seongsu-popup-contract", "no live event provider is configured")
    if not healthy_providers:
        return Result("FAIL", "seongsu-popup-contract", "configured event providers are not verified healthy")
    if status.get("eventMode") != "LIVE_OR_CRAWLED_READY":
        return Result("FAIL", "seongsu-popup-contract", "eventMode must report LIVE_OR_CRAWLED_READY for a healthy provider")
    return Result(
        "WARN",
        "seongsu-popup-contract",
        "healthy live provider returned no current verified Seongsu popup; empty UI is truthful",
    )


def check_insights_contract(
    payload: Any,
    *,
    today: date | None = None,
    now: datetime | None = None,
) -> Result:
    failure, data = envelope_data(payload, "seongsu-popup-contract")
    if failure is not None or data is None:
        return failure or Result("FAIL", "seongsu-popup-contract", "data object is required")
    events_payload = data.get("events")
    if not isinstance(events_payload, dict):
        return Result("FAIL", "seongsu-popup-contract", "data.events object is required")
    events = events_payload.get("events")
    if not isinstance(events, list):
        return Result("FAIL", "seongsu-popup-contract", "data.events.events list is required")
    if not events:
        if events_payload.get("dataFreshness") != "NO_VERIFIED_DATA":
            return Result("FAIL", "seongsu-popup-contract", "empty events must report dataFreshness=NO_VERIFIED_DATA")
        return empty_event_provider_contract(data.get("status"), now=now)

    current_date = today or datetime.now(ZoneInfo("Asia/Seoul")).date()
    for index, item in enumerate(events):
        error = popup_validation_error(item, current_date)
        if error:
            return Result("FAIL", "seongsu-popup-contract", f"events[{index}]: {error}")
    return Result("PASS", "seongsu-popup-contract", f"{len(events)} current Seongsu popup records verified")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site-base-url", default="https://nugulmap.com")
    parser.add_argument("--api-base-url", default="https://api.nugulmap.com/api")
    parser.add_argument("--timeout", type=float, default=12.0)
    args = parser.parse_args()

    results = [
        check_page(args.site_base_url, path, args.timeout)
        for path in ("/privacy", "/account-deletion", "/connected-accounts", "/support", "/terms")
    ]

    api_base = args.api_base_url.rstrip("/")
    health_result, health_payload = parse_json(f"{api_base}/actuator/health", args.timeout)
    if health_result.status == "PASS":
        health_result = check_health_contract(health_payload)
    else:
        health_result = Result(health_result.status, "api-health", health_result.detail)
    results.append(health_result)

    bounds = "minLat=37.48&minLng=126.90&maxLat=37.58&maxLng=127.12"
    zones_result, zones_payload = parse_json(f"{api_base}/zones/bounds?{bounds}", args.timeout)
    results.append(Result(zones_result.status, "public-zones", zones_result.detail))
    if zones_payload is not None:
        results.append(check_zone_privacy(zones_payload))
        records_result = check_zone_records(zones_payload)
        results.append(records_result)
        if records_result.status == "PASS":
            zone_id = zones_payload["data"]["zones"][0]["id"]
            detail_result, detail = parse_json(f"{api_base}/zones/{zone_id}", args.timeout)
            results.append(Result(detail_result.status, "public-zone-detail", detail_result.detail))
            if detail is not None:
                failure, data = envelope_data(detail, "public-zone-detail")
                if failure:
                    results.append(failure)
                else:
                    wrapped = {"success": True, "data": {"zones": [data.get("zone")]}}
                    results.extend((check_zone_records(wrapped), check_zone_privacy(wrapped)))
            reviews_result, reviews = parse_json(f"{api_base}/zones/{zone_id}/reviews", args.timeout)
            results.append(Result(reviews_result.status, "public-zone-reviews", reviews_result.detail))
            if reviews is not None:
                failure, data = envelope_data(reviews, "public-zone-reviews")
                if failure:
                    results.append(failure)
                elif not isinstance(data.get("reviews"), list):
                    results.append(Result("FAIL", "public-zone-reviews", "data.reviews list is required"))

    search_query = urllib.parse.urlencode({"keyword": "서울", "limit": 10})
    search_result, search = parse_json(f"{api_base}/zones/search?{search_query}", args.timeout)
    results.append(Result(search_result.status, "public-zone-search", search_result.detail))
    if search is not None:
        results.extend((check_zone_records(search), check_zone_privacy(search)))

    moderation_result, moderation_payload = parse_json(f"{api_base}/public/moderation", args.timeout)
    results.append(Result(moderation_result.status, "public-moderation", moderation_result.detail))
    if moderation_payload is not None:
        results.append(check_moderation_contract(moderation_payload))

    for path in (
        "/insights/status",
        "/recommendations/places?intent=CAFE&lat=37.5665&lng=126.978&limit=1",
        "/location-letters?lat=37.5665&lng=126.978",
    ):
        results.append(check_discovery_disabled(api_base, path, args.timeout))

    for result in results:
        print(f"[{result.status}] {result.name}: {result.detail}")

    failures = sum(result.status == "FAIL" for result in results)
    warnings = sum(result.status == "WARN" for result in results)
    passes = len(results) - failures - warnings
    print(f"\nSummary: PASS={passes} WARN={warnings} FAIL={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
