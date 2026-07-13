import argparse
import hashlib
import ipaddress
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any, Callable
from urllib.parse import urljoin, urlparse
from xml.etree import ElementTree

import requests
from bs4 import BeautifulSoup


DEFAULT_USER_AGENT = "NugulMapSeason3Bot/1.0 (+https://nugulmap.com)"
DATA_SCRIPTS_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG_PATH = DATA_SCRIPTS_ROOT / "config" / "popup_trend_sources.json"
DEFAULT_OUTPUT_PATH = DATA_SCRIPTS_ROOT / "data" / "popup-trends.json"
DEFAULT_STATE_PATH = DATA_SCRIPTS_ROOT / "data" / "popup-source-state.json"
DEFAULT_STATUS_PATH = DATA_SCRIPTS_ROOT / "data" / "popup-source-status.json"
DEFAULT_REVIEW_PATH = DATA_SCRIPTS_ROOT / "data" / "popup-verification-queue.json"
EVENT_TITLE_KEYS = ("title", "name", "eventTitle", "displayName", "TITLE")
LATITUDE_KEYS = ("latitude", "lat", "mapy", "y", "LAT")
LONGITUDE_KEYS = ("longitude", "lng", "lon", "mapx", "x", "LOT")
START_DATE_KEYS = (
    "startDate",
    "eventStartDate",
    "eventstartdate",
    "start_date",
    "start",
    "STRTDATE",
    "RGSTDATE",
)
END_DATE_KEYS = ("endDate", "eventEndDate", "eventenddate", "end_date", "end", "END_DATE")
PERIOD_KEYS = ("period", "DATE", "date")
ADDRESS_KEYS = ("address", "addr1", "roadAddress", "locationName", "placeName", "PLACE", "GUNAME")
IMAGE_KEYS = ("imageUrl", "image", "firstimage", "thumbnail", "thumbnailUrl", "MAIN_IMG")
URL_KEYS = (
    "detailUrl",
    "ORG_LINK",
    "HMPG_ADDR",
    "eventhomepage",
    "EVENTHOMEPAGE",
    "url",
    "link",
    "href",
    "contentUrl",
)
CONTENT_ID_KEYS = ("sourceContentId", "contentId", "contentid", "CONTENTID", "CULTCODE", "EVENT_ID", "id")
SEOUL_BOUNDS = {
    "min_lat": 37.40,
    "max_lat": 37.72,
    "min_lng": 126.76,
    "max_lng": 127.20,
}
SEOUL_TIMEZONE = timezone(timedelta(hours=9))
TRANSIENT_HTTP_STATUSES = {408, 425, 429, 500, 502, 503, 504}
TOBACCO_NICOTINE_PATTERN = re.compile(
    r"(?:담배|니코틴|베이프|궐련|아이코스)"
    r"|(?<![a-z0-9])(?:tobacco|nicotine|iqos|juul|vap(?:e|es|ing)|vaporizer(?:s)?|vaporiser(?:s)?|cigarette(?:s)?|e[\s_-]?cig(?:arette)?s?)(?![a-z0-9])",
    flags=re.IGNORECASE,
)
TOBACCO_METADATA_KEYS = {
    "title",
    "name",
    "eventtitle",
    "displayname",
    "description",
    "summary",
    "excerpt",
    "content",
    "topic",
    "category",
    "kind",
    "codename",
    "period",
    "date",
    "address",
    "addr1",
    "addr2",
    "roadaddress",
    "locationname",
    "placename",
    "place",
    "guname",
    "detailurl",
    "sourceurl",
    "officialsourceurl",
    "orglink",
    "hmpgaddr",
    "eventhomepage",
    "url",
    "link",
    "href",
    "contenturl",
}

# The runner polls frequently, but each source is fetched only on its own
# cadence. Jitter is a deterministic per-source offset so mall domains are not
# all hit at the same second after a deploy or restart.
DEFAULT_SOURCE_POLICIES: dict[str, dict[str, int]] = {
    "seoul_culture_api": {"intervalSeconds": 86_400, "maxJitterSeconds": 0},
    "tour_api": {"intervalSeconds": 21_600, "maxJitterSeconds": 300},
    "official_mall": {"intervalSeconds": 14_400, "maxJitterSeconds": 900},
    "rss": {"intervalSeconds": 21_600, "maxJitterSeconds": 300},
    "discovery": {"intervalSeconds": 43_200, "maxJitterSeconds": 600},
    "partner_api": {"intervalSeconds": 21_600, "maxJitterSeconds": 300},
    "manual": {"intervalSeconds": 86_400, "maxJitterSeconds": 0},
}


@dataclass
class SourceFetchResult:
    source_id: str
    source_type: str
    status: str
    records: list[dict[str, Any]]
    state: dict[str, Any]
    candidates: list[dict[str, Any]] = field(default_factory=list)
    persist_records: bool = True
    replace_records: bool = False
    replace_candidates: bool = False
    http_status: int | None = None
    message: str | None = None
    refresh_retained_records: bool = False


@dataclass
class CollectionBatch:
    records: list[dict[str, Any]]
    results: list[SourceFetchResult]
    state: dict[str, Any]
    candidates: list[dict[str, Any]] = field(default_factory=list)


def read_config(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"sources": []}
    return expand_env_placeholders(json.loads(path.read_text(encoding="utf-8")))


def expand_env_placeholders(value: Any) -> Any:
    if isinstance(value, str):
        return expand_env_string(value)
    if isinstance(value, list):
        return [expand_env_placeholders(item) for item in value]
    if isinstance(value, dict):
        return {key: expand_env_placeholders(item) for key, item in value.items()}
    return value


def expand_env_string(value: str) -> str:
    pattern = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-(.*?))?\}")

    def replace(match: re.Match[str]) -> str:
        env_name = match.group(1)
        fallback = match.group(2)
        env_value = os.environ.get(env_name)
        if env_value is not None and env_value.strip():
            return env_value
        return fallback or ""

    previous = value
    while True:
        expanded = pattern.sub(replace, previous)
        if expanded == previous:
            return expanded
        previous = expanded


def positive_int(value: Any, fallback: int, minimum: int = 1, maximum: int | None = None) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return fallback
    if parsed < minimum:
        return fallback
    return min(parsed, maximum) if maximum is not None else parsed


def utc_now(value: datetime | None = None) -> datetime:
    current = value or datetime.now(timezone.utc)
    if current.tzinfo is None:
        return current.replace(tzinfo=timezone.utc)
    return current.astimezone(timezone.utc)


def iso_timestamp(value: datetime) -> str:
    return utc_now(value).isoformat()


def parse_timestamp(value: Any) -> datetime | None:
    text = text_or_none(value)
    if not text:
        return None
    try:
        return utc_now(datetime.fromisoformat(text.replace("Z", "+00:00")))
    except ValueError:
        return None


def normalized_hosts(source: dict[str, Any]) -> list[str]:
    hosts = source.get("allowedHosts")
    if not isinstance(hosts, list):
        return []
    return sorted(
        {
            str(host).strip().lower().rstrip(".")
            for host in hosts
            if text_or_none(host)
        }
    )


def host_is_allowed(host: str, allowed_hosts: list[str]) -> bool:
    normalized = host.strip().lower().rstrip(".")
    return any(normalized == allowed or normalized.endswith(f".{allowed}") for allowed in allowed_hosts)


def allowlisted_http_url(value: Any, source: dict[str, Any]) -> str | None:
    url = safe_http_url(value, allow_private=private_test_hosts_allowed(source))
    if not url:
        return None
    allowed_hosts = normalized_hosts(source)
    if not allowed_hosts:
        return url
    host = (urlparse(url).hostname or "").lower()
    return url if host_is_allowed(host, allowed_hosts) else None


def private_test_hosts_allowed(source: dict[str, Any]) -> bool:
    return (
        source.get("testOnlyAllowPrivateHost") is True
        and os.environ.get("NUGULMAP_ALLOW_PRIVATE_TEST_HOSTS", "").strip().lower() == "true"
    )


def source_pagination(source: dict[str, Any]) -> dict[str, int] | None:
    pagination = source.get("pagination")
    if not isinstance(pagination, dict) or pagination.get("enabled") is not True:
        return None
    return {
        "pageSize": positive_int(pagination.get("pageSize"), 1_000, minimum=1, maximum=1_000),
        "maxPages": positive_int(pagination.get("maxPages"), 25, minimum=1, maximum=50),
    }


def validate_source_definition(source: dict[str, Any]) -> str | None:
    source_id = text_or_none(source.get("id"))
    if not source_id:
        return "id is required"

    source_type = text_or_none(source.get("sourceType"))
    if not source_type or source_type not in DEFAULT_SOURCE_POLICIES:
        return f"sourceType must be one of {', '.join(sorted(DEFAULT_SOURCE_POLICIES))}"

    if source_type == "manual":
        return None

    url = safe_http_url(
        source.get("url"),
        allow_private=private_test_hosts_allowed(source),
    )
    if not url:
        return "url must be an absolute HTTP(S) URL"

    official_url = safe_http_url(source.get("officialSourceUrl"))
    if not official_url:
        return "officialSourceUrl must be an absolute HTTP(S) URL"

    allowed_hosts = normalized_hosts(source)
    if not allowed_hosts:
        return "allowedHosts allowlist is required"

    fetch_host = (urlparse(url).hostname or "").lower()
    official_host = (urlparse(official_url).hostname or "").lower()
    if fetch_host == "instagram.com" or fetch_host.endswith(".instagram.com"):
        return "direct Instagram scraping is prohibited; use an approved Graph API or partner feed"
    is_naver_search = fetch_host == "openapi.naver.com" or fetch_host.endswith(".openapi.naver.com")
    if is_naver_search and source_type != "discovery":
        return "Naver search results must use sourceType discovery and cannot be persisted"
    if not host_is_allowed(fetch_host, allowed_hosts):
        return f"fetch URL host {fetch_host!r} is not in the allowlist"
    if not host_is_allowed(official_host, allowed_hosts):
        return f"officialSourceUrl host {official_host!r} is not in the allowlist"

    pagination = source_pagination(source)
    if pagination:
        if source_type != "seoul_culture_api":
            return "pagination is currently supported only for seoul_culture_api sources"
        if "{{PAGE_START}}" not in url or "{{PAGE_END}}" not in url:
            return "paginated source URL must include {{PAGE_START}} and {{PAGE_END}}"

    if source_type == "discovery":
        if source.get("persistRecords") is not False:
            return "discovery sources must explicitly set persistRecords to false"
        if source.get("publicationPolicy") != "review_required":
            return "discovery sources must use publicationPolicy review_required"

    if source_type != "discovery" and source.get("persistRecords") is not False:
        if source.get("publicationPolicy") != "allowed_with_attribution":
            return "publicationPolicy must be allowed_with_attribution for persisted network sources"
        if text_or_none(source.get("attribution")) is None:
            return "attribution is required for persisted network sources"
        if text_or_none(source.get("license")) is None:
            return "license is required for persisted network sources"
        if safe_http_url(source.get("licenseUrl")) is None:
            return "licenseUrl must be a public absolute HTTP(S) URL"

    if source.get("parser") == "skt_newsroom_seongsu":
        for policy_key in ("robotsUrl", "usagePolicyUrl"):
            policy_url = allowlisted_http_url(source.get(policy_key), source)
            if not policy_url:
                return f"{policy_key} must be an allowlisted HTTP(S) URL"
    return None


def source_schedule(source: dict[str, Any]) -> dict[str, int]:
    source_type = text_or_none(source.get("sourceType")) or "manual"
    policy = DEFAULT_SOURCE_POLICIES.get(source_type, DEFAULT_SOURCE_POLICIES["manual"])
    schedule = source.get("schedule") if isinstance(source.get("schedule"), dict) else {}
    interval = positive_int(
        schedule.get("intervalSeconds", source.get("intervalSeconds")),
        policy["intervalSeconds"],
        minimum=60,
    )
    max_jitter = positive_int(
        schedule.get("maxJitterSeconds", source.get("maxJitterSeconds")),
        policy["maxJitterSeconds"],
        minimum=0,
    )
    if max_jitter <= 0:
        jitter = 0
    else:
        source_identity = text_or_none(source.get("id")) or text_or_none(source.get("url")) or source_type
        digest = int(hashlib.sha256(source_identity.encode("utf-8")).hexdigest()[:8], 16)
        jitter = digest % (max_jitter * 2 + 1) - max_jitter
    return {"intervalSeconds": interval, "jitterSeconds": jitter}


def scheduled_next_run(source: dict[str, Any], now: datetime) -> str:
    schedule = source_schedule(source)
    delay = max(60, schedule["intervalSeconds"] + schedule["jitterSeconds"])
    return iso_timestamp(utc_now(now) + timedelta(seconds=delay))


def render_source_url(
    source: dict[str, Any],
    now: datetime,
    *,
    page_start: int | None = None,
    page_end: int | None = None,
) -> str:
    current = utc_now(now)
    pagination = source_pagination(source)
    if pagination:
        page_start = page_start or 1
        page_end = page_end or pagination["pageSize"]
    replacements = {
        "{{TODAY}}": current.strftime("%Y%m%d"),
        "{{TODAY_MINUS_7D}}": (current - timedelta(days=7)).strftime("%Y%m%d"),
        "{{TODAY_PLUS_90D}}": (current + timedelta(days=90)).strftime("%Y%m%d"),
        "{{PAGE_START}}": str(page_start or 1),
        "{{PAGE_END}}": str(page_end or 1),
    }
    rendered = str(source.get("url") or "")
    for placeholder, value in replacements.items():
        rendered = rendered.replace(placeholder, value)
    return rendered


def source_is_due(source: dict[str, Any], cached_state: dict[str, Any], now: datetime) -> bool:
    next_run_at = parse_timestamp(cached_state.get("nextRunAt"))
    return next_run_at is None or next_run_at <= utc_now(now)


def normalized_body_hash(body: str, content_type: str = "") -> str:
    normalized = body
    if "json" in content_type.lower():
        try:
            normalized = json.dumps(
                json.loads(body),
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        except (TypeError, ValueError):
            normalized = re.sub(r"\s+", " ", body).strip()
    else:
        normalized = re.sub(r"<!--.*?-->", "", body, flags=re.DOTALL)
        normalized = re.sub(r">\s+<", "><", normalized)
        normalized = re.sub(r"\s+", " ", normalized).strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def retry_after_seconds(headers: dict[str, Any], now: datetime, fallback: float, maximum: float) -> float:
    raw = text_or_none(headers.get("Retry-After"))
    if raw:
        try:
            return max(0.0, min(float(raw), maximum))
        except ValueError:
            try:
                retry_at = utc_now(parsedate_to_datetime(raw))
                return max(0.0, min((retry_at - utc_now(now)).total_seconds(), maximum))
            except (TypeError, ValueError, OverflowError):
                pass
    return max(0.0, min(fallback, maximum))


def seoul_open_api_page_contract(response: Any) -> tuple[int | None, int | None, str | None]:
    """Return the official total and raw row count for one Seoul API page.

    HTTP 200 alone is not a successful Seoul OpenAPI response. Error payloads
    and truncated pages must fail before any filtered subset can replace the
    last-good popup file.
    """
    try:
        payload = response.json()
    except (TypeError, ValueError):
        return None, None, "official pagination response is not valid JSON"
    if not isinstance(payload, dict):
        return None, None, "official pagination response must be a JSON object"

    container = payload.get("culturalEventInfo")
    if not isinstance(container, dict):
        return None, None, "official pagination response envelope is missing"

    result = container.get("RESULT")
    if isinstance(result, dict):
        result_code = text_or_none(result.get("CODE"))
        if result_code and result_code.upper() != "INFO-000":
            return None, None, f"official pagination response failed with {result_code}"

    total_value = container.get("list_total_count")
    if total_value is None:
        total_value = container.get("LIST_TOTAL_COUNT")
    try:
        total_count = int(str(total_value).replace(",", ""))
    except (TypeError, ValueError):
        return None, None, "official pagination total count is missing"
    if total_count < 0:
        return None, None, "official pagination total count is invalid"

    rows = container.get("row")
    if rows is None:
        rows = container.get("ROW")
    if rows is None and total_count == 0:
        rows = []
    if not isinstance(rows, list):
        return None, None, "official pagination row list is missing"
    if any(not isinstance(row, dict) for row in rows):
        return None, None, "official pagination row list contains a non-object value"
    return total_count, len(rows), None


def response_collection_hash(responses: list[Any]) -> str:
    page_hashes = [
        normalized_body_hash(
            str(response.text),
            str(response.headers.get("content-type", "")),
        )
        for response in responses
    ]
    if len(page_hashes) == 1:
        return page_hashes[0]
    return hashlib.sha256("|".join(page_hashes).encode("utf-8")).hexdigest()


def fetch_additional_page(
    url: str,
    *,
    headers: dict[str, str],
    timeout: int,
    request_get: Callable[..., Any],
    sleep_fn: Callable[[float], None],
    now: datetime,
    max_attempts: int,
    base_backoff: float,
    max_backoff: float,
    max_inline_retry: float,
) -> tuple[Any | None, dict[str, Any] | None]:
    last_delay = base_backoff
    for attempt in range(max_attempts):
        try:
            response = request_get(
                url,
                headers=headers,
                timeout=timeout,
                allow_redirects=False,
            )
        except requests.RequestException as error:
            last_delay = min(base_backoff * (2**attempt), max_backoff)
            if attempt + 1 < max_attempts and last_delay <= max_inline_retry:
                sleep_fn(last_delay)
                continue
            return None, {
                "status": "error",
                "message": f"{error.__class__.__name__}: paginated request failed",
                "httpStatus": None,
                "nextDelay": last_delay,
            }

        status_code = int(response.status_code)
        if status_code in {401, 403}:
            return None, {
                "status": "disabled_auth",
                "message": f"HTTP {status_code}",
                "httpStatus": status_code,
                "nextDelay": None,
            }
        if status_code in TRANSIENT_HTTP_STATUSES:
            exponential_delay = base_backoff * (2**attempt)
            last_delay = retry_after_seconds(response.headers, now, exponential_delay, max_backoff)
            if attempt + 1 < max_attempts and last_delay <= max_inline_retry:
                sleep_fn(last_delay)
                continue
            return None, {
                "status": "backoff",
                "message": f"HTTP {status_code}",
                "httpStatus": status_code,
                "nextDelay": last_delay,
            }
        if 300 <= status_code < 400:
            return None, {
                "status": "error",
                "message": f"unexpected HTTP redirect {status_code}",
                "httpStatus": status_code,
                "nextDelay": base_backoff,
            }
        try:
            response.raise_for_status()
        except requests.RequestException as error:
            last_delay = min(base_backoff * (2**attempt), max_backoff)
            return None, {
                "status": "error",
                "message": f"HTTP {status_code} ({error.__class__.__name__})",
                "httpStatus": status_code,
                "nextDelay": last_delay,
            }
        return response, None
    return None, {
        "status": "error",
        "message": "paginated retry loop exhausted",
        "httpStatus": None,
        "nextDelay": last_delay,
    }


def source_state_base(source: dict[str, Any], cached_state: dict[str, Any], now: datetime) -> dict[str, Any]:
    state = dict(cached_state)
    state.update(
        {
            "sourceId": text_or_none(source.get("id")) or "unknown-source",
            "sourceType": text_or_none(source.get("sourceType")) or "unknown",
            "sourceUrl": allowlisted_http_url(source.get("officialSourceUrl"), source),
            "lastAttemptAt": iso_timestamp(now),
        }
    )
    return state


def stable_id(source_url: str, title: str) -> str:
    digest = hashlib.sha1(f"{source_url}|{title}".encode("utf-8")).hexdigest()[:12]
    return f"popup-{digest}"


def text_or_none(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def normalize_date_text(value: Any) -> str | None:
    text = text_or_none(value)
    if not text:
        return None

    match = re.search(r"\d{4}[-.]\d{2}[-.]\d{2}", text)
    if match:
        candidate = match.group().replace(".", "-")
        try:
            return date.fromisoformat(candidate).isoformat()
        except ValueError:
            return None

    match = re.search(r"\d{8}", text)
    if match:
        raw = match.group()
        candidate = f"{raw[:4]}-{raw[4:6]}-{raw[6:8]}"
        try:
            return date.fromisoformat(candidate).isoformat()
        except ValueError:
            return None

    return None


def parse_number(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(str(value).strip().replace(",", ""))
    except ValueError:
        return None


def read_first(raw: dict[str, Any], keys: tuple[str, ...]) -> Any:
    for key in keys:
        value = raw.get(key)
        if value is not None:
            return value
    return None


def read_first_allowlisted_url(
    raw: dict[str, Any],
    keys: tuple[str, ...],
    source: dict[str, Any],
) -> str | None:
    for key in keys:
        url = allowlisted_http_url(raw.get(key), source)
        if url:
            return url
    return None


def first_nested(raw: dict[str, Any], keys: tuple[str, ...]) -> Any:
    value = read_first(raw, keys)
    if value is not None:
        return value

    for container_key in ("location", "place", "venue"):
        container = raw.get(container_key)
        if not isinstance(container, dict):
            continue
        value = read_first(container, keys)
        if value is not None:
            return value
        geo = container.get("geo")
        if isinstance(geo, dict):
            value = read_first(geo, keys)
            if value is not None:
                return value

    geo = raw.get("geo")
    if isinstance(geo, dict):
        value = read_first(geo, keys)
        if value is not None:
            return value

    return None


def read_image_url(value: Any) -> str | None:
    if isinstance(value, list):
        for item in value:
            parsed = read_image_url(item)
            if parsed:
                return parsed
        return None
    if isinstance(value, dict):
        return text_or_none(value.get("url") or value.get("src") or value.get("contentUrl"))
    return text_or_none(value)


def safe_http_url(value: Any, *, allow_private: bool = False) -> str | None:
    """Return a link only when it is an absolute HTTP(S) URL.

    Some public APIs wrap their homepage field in a small HTML anchor. Extract
    only that href and never pass script/data schemes or a feed URL containing
    an API key through to clients.
    """
    text = text_or_none(value)
    if not text:
        return None

    if "<" in text and ">" in text:
        soup = BeautifulSoup(text, "html.parser")
        anchor = soup.find("a", href=True)
        text = text_or_none(anchor.get("href")) if anchor else None
        if not text:
            return None

    parsed = urlparse(text)
    host = (parsed.hostname or "").strip().lower().rstrip(".")
    if (
        parsed.scheme.lower() not in {"http", "https"}
        or not parsed.netloc
        or not host
        or parsed.username is not None
        or parsed.password is not None
        or (not allow_private and not is_public_link_host(host))
    ):
        return None
    return text


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


def has_explicit_popup_title(value: Any) -> bool:
    title = (text_or_none(value) or "").casefold()
    return "팝업" in title or re.search(r"(?<![a-z])popup(?![a-z])", title) is not None


def contains_tobacco_or_nicotine_terms(*metadata: Any) -> bool:
    searchable: list[str] = []
    for value in metadata:
        if not isinstance(value, dict):
            continue
        for raw_key, raw_value in value.items():
            normalized_key = re.sub(r"[^a-z0-9]", "", str(raw_key).casefold())
            if normalized_key not in TOBACCO_METADATA_KEYS or raw_value is None:
                continue
            if isinstance(raw_value, (dict, list)):
                searchable.append(json.dumps(raw_value, ensure_ascii=False, sort_keys=True))
            else:
                searchable.append(str(raw_value))
    return TOBACCO_NICOTINE_PATTERN.search(" ".join(searchable)) is not None


def normalize_record(raw: dict[str, Any], source: dict[str, Any]) -> dict[str, Any] | None:
    # Search APIs such as Naver are discovery-only under their platform terms.
    # Never let those result objects enter the persisted popup dataset.
    if source.get("sourceType") == "discovery":
        return None
    if contains_tobacco_or_nicotine_terms(raw, source):
        return None

    title = text_or_none(read_first(raw, EVENT_TITLE_KEYS))
    latitude = parse_number(first_nested(raw, LATITUDE_KEYS) or source.get("latitude"))
    longitude = parse_number(first_nested(raw, LONGITUDE_KEYS) or source.get("longitude"))
    source_url = read_first_allowlisted_url(raw, URL_KEYS, source) or allowlisted_http_url(
        source.get("detailUrl"), source
    )
    official_source_url = allowlisted_http_url(source.get("officialSourceUrl"), source)
    image_url = read_image_url(first_nested(raw, IMAGE_KEYS))

    if not title or latitude is None or longitude is None:
        return None

    start_date = normalize_date_text(read_first(raw, START_DATE_KEYS))
    end_date = normalize_date_text(read_first(raw, END_DATE_KEYS))
    period = text_or_none(read_first(raw, PERIOD_KEYS))
    if not period and start_date:
        period = start_date if not end_date or end_date == start_date else f"{start_date}-{end_date}"

    generated_id = stable_id(source_url or "no-detail-url", title)
    source_content_id = text_or_none(read_first(raw, CONTENT_ID_KEYS))
    if safe_http_url(source_content_id):
        source_content_id = None

    is_manual = source.get("manual") is True or not text_or_none(source.get("url"))

    kind = text_or_none(raw.get("kind")) or text_or_none(source.get("kind")) or "popup"
    if source.get("sourceType") == "seoul_culture_api" and has_explicit_popup_title(title):
        kind = "popup"

    record = {
        "id": text_or_none(raw.get("id")) or generated_id,
        "title": title,
        "kind": kind,
        "period": period or text_or_none(source.get("period")) or "최근 후보",
        "startDate": start_date,
        "endDate": end_date,
        "latitude": latitude,
        "longitude": longitude,
        "address": text_or_none(first_nested(raw, ADDRESS_KEYS)) or text_or_none(source.get("address")) or "",
        "imageUrl": image_url,
        "source": text_or_none(raw.get("source")) or text_or_none(source.get("source")) or "CRAWLED_POPUP_TREND",
        "sourceId": text_or_none(source.get("id")),
        "sourceType": text_or_none(source.get("sourceType")),
        "sourceUrl": official_source_url,
        "sourceContentId": source_content_id or generated_id,
        "detailUrl": source_url,
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "collectionMode": "MANUAL" if is_manual else "NETWORK",
    }

    # Preserve the minimum rights/provenance contract needed to audit rows
    # without exposing API credentials or raw provider payloads to clients.
    for field_name in (
        "attribution",
        "license",
        "licenseUrl",
        "usagePolicyUrl",
        "publicationPolicy",
    ):
        field_value = text_or_none(source.get(field_name))
        if field_value:
            record[field_name] = field_value
    record["updateCadenceSeconds"] = source_schedule(source)["intervalSeconds"]
    if (
        record["collectionMode"] == "NETWORK"
        and record.get("publicationPolicy") == "allowed_with_attribution"
    ):
        record["verificationStatus"] = "VERIFIED_SOURCE_RIGHTS"
    return record


def validate_record(record: dict[str, Any]) -> bool:
    if not text_or_none(record.get("id")) or not text_or_none(record.get("title")):
        return False

    latitude = parse_number(record.get("latitude"))
    longitude = parse_number(record.get("longitude"))
    if latitude is None or longitude is None:
        return False

    return (
        SEOUL_BOUNDS["min_lat"] <= latitude <= SEOUL_BOUNDS["max_lat"]
        and SEOUL_BOUNDS["min_lng"] <= longitude <= SEOUL_BOUNDS["max_lng"]
    )


def seoul_reference_date(as_of: date | datetime | None = None) -> date:
    if isinstance(as_of, datetime):
        return utc_now(as_of).astimezone(SEOUL_TIMEZONE).date()
    if isinstance(as_of, date):
        return as_of
    return datetime.now(SEOUL_TIMEZONE).date()


def is_verifiable_record(
    record: dict[str, Any],
    *,
    as_of: date | datetime | None = None,
) -> bool:
    """Whether a record can count toward the production collection gate."""
    normalized_start = normalize_date_text(record.get("startDate"))
    normalized_end = normalize_date_text(record.get("endDate"))
    start_date = date.fromisoformat(normalized_start) if normalized_start else None
    end_date = date.fromisoformat(normalized_end) if normalized_end else None
    base_contract = (
        record.get("collectionMode") == "NETWORK"
        and start_date is not None
        and end_date is not None
        and start_date <= end_date
        and end_date >= seoul_reference_date(as_of)
        and text_or_none(record.get("address")) is not None
        and safe_http_url(record.get("detailUrl")) is not None
        and validate_record(record)
        and not contains_tobacco_or_nicotine_terms(record)
    )
    if not base_contract:
        return False

    return (
        record.get("verificationStatus") == "VERIFIED_SOURCE_RIGHTS"
        and text_or_none(record.get("attribution")) is not None
        and text_or_none(record.get("license")) is not None
        and safe_http_url(record.get("licenseUrl")) is not None
        and safe_http_url(record.get("sourceUrl")) is not None
        and record.get("publicationPolicy") == "allowed_with_attribution"
    )


def dedupe_key(record: dict[str, Any]) -> str:
    title = text_or_none(record.get("title")) or ""
    start_date = text_or_none(record.get("startDate")) or ""
    latitude = parse_number(record.get("latitude"))
    longitude = parse_number(record.get("longitude"))
    if latitude is None or longitude is None:
        return text_or_none(record.get("id")) or title
    return "|".join(
        (
            re.sub(r"\s+", " ", title).strip().lower(),
            start_date,
            f"{latitude:.4f}",
            f"{longitude:.4f}",
        )
    )


def extract_json_ld_events(soup: BeautifulSoup, source: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        try:
            payload = json.loads(script.string or "")
        except json.JSONDecodeError:
            continue

        candidates = payload if isinstance(payload, list) else [payload]
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            graph = candidate.get("@graph")
            if isinstance(graph, list):
                candidates.extend([item for item in graph if isinstance(item, dict)])
                continue
            event_type = candidate.get("@type")
            if isinstance(event_type, list):
                is_event = "Event" in event_type
            else:
                is_event = event_type == "Event"
            if not is_event:
                continue
            location = candidate.get("location") if isinstance(candidate.get("location"), dict) else {}
            geo = location.get("geo") if isinstance(location.get("geo"), dict) else {}
            image = candidate.get("image")
            if isinstance(image, list):
                image = image[0] if image else None
            records.append(
                {
                    "title": candidate.get("name"),
                    "startDate": candidate.get("startDate"),
                    "endDate": candidate.get("endDate"),
                    "address": location.get("name") or location.get("address"),
                    "latitude": geo.get("latitude"),
                    "longitude": geo.get("longitude"),
                    "imageUrl": image,
                    "url": candidate.get("url") or source.get("url"),
                }
            )
    return records


def walk_dicts(value: Any, limit: int = 250) -> list[dict[str, Any]]:
    found: list[dict[str, Any]] = []
    stack = [value]

    while stack and len(found) < limit:
        current = stack.pop()
        if isinstance(current, dict):
            found.append(current)
            stack.extend(current.values())
        elif isinstance(current, list):
            stack.extend(current)

    return found


def extract_explicit_period_dates(value: Any) -> tuple[str, str] | None:
    text = text_or_none(value)
    if not text:
        return None
    parsed: list[str] = []
    for year_text, month_text, day_text in re.findall(
        r"(?<!\d)(\d{2}|\d{4})[./-](\d{1,2})[./-](\d{1,2})(?!\d)", text
    ):
        year = int(year_text)
        if year < 100:
            year += 2000
        try:
            parsed.append(datetime(year, int(month_text), int(day_text)).date().isoformat())
        except ValueError:
            continue
    return (parsed[0], parsed[1]) if len(parsed) >= 2 else None


def record_matches_source_filters(record: dict[str, Any], source: dict[str, Any]) -> bool:
    """Apply opt-in source filters after normalization.

    The Seoul culture feed is city-wide.  A source can therefore pin a
    publishable subset to both explicit place words and a small coordinate
    box, preventing a generic Seoul row from being mislabeled as Seongsu.
    """
    filters = source.get("recordFilter")
    if not isinstance(filters, dict):
        return True

    location_searchable = " ".join(
        str(record.get(key) or "") for key in ("title", "address")
    ).casefold()
    topic_searchable = " ".join(
        str(record.get(key) or "") for key in ("title", "kind", "period")
    ).casefold()
    location_terms = filters.get("locationTerms")
    if isinstance(location_terms, list):
        normalized_terms = [str(term).casefold() for term in location_terms if text_or_none(term)]
        if normalized_terms and not any(term in location_searchable for term in normalized_terms):
            return False

    title_terms = filters.get("titleTerms")
    if isinstance(title_terms, list):
        title_searchable = str(record.get("title") or "").casefold()
        normalized_terms = [str(term).casefold() for term in title_terms if text_or_none(term)]
        if normalized_terms and not any(term in title_searchable for term in normalized_terms):
            return False

    topic_terms = filters.get("topicTerms")
    if isinstance(topic_terms, list):
        normalized_terms = [str(term).casefold() for term in topic_terms if text_or_none(term)]
        if normalized_terms and not any(term in topic_searchable for term in normalized_terms):
            return False

    bounds = filters.get("bounds")
    if isinstance(bounds, dict):
        latitude = parse_number(record.get("latitude"))
        longitude = parse_number(record.get("longitude"))
        min_lat = parse_number(bounds.get("minLatitude"))
        max_lat = parse_number(bounds.get("maxLatitude"))
        min_lng = parse_number(bounds.get("minLongitude"))
        max_lng = parse_number(bounds.get("maxLongitude"))
        if None in (latitude, longitude, min_lat, max_lat, min_lng, max_lng):
            return False
        if not (min_lat <= latitude <= max_lat and min_lng <= longitude <= max_lng):
            return False

    return True


def extract_korean_date_range(value: Any, reference_date: date) -> tuple[str, str] | None:
    """Extract an explicit Korean month/day range using the post year.

    SKT newsroom posts use forms such as ``7월 2일부터 9월 13일까지``.
    The end year rolls forward only when the end month precedes the start
    month.  A single date is intentionally not accepted for verification.
    """
    text = text_or_none(value)
    if not text:
        return None

    match = re.search(
        r"(?:(\d{4})\s*년\s*)?(\d{1,2})\s*월\s*(\d{1,2})\s*일\s*"
        r"(?:부터|[-~–—])\s*"
        r"(?:(\d{4})\s*년\s*)?(\d{1,2})\s*월\s*(\d{1,2})\s*일(?:까지)?",
        text,
    )
    if not match:
        return None

    start_year = int(match.group(1) or reference_date.year)
    start_month = int(match.group(2))
    start_day = int(match.group(3))
    end_year = int(match.group(4) or start_year)
    end_month = int(match.group(5))
    end_day = int(match.group(6))
    if match.group(4) is None and end_month < start_month:
        end_year += 1
    try:
        start = date(start_year, start_month, start_day)
        end = date(end_year, end_month, end_day)
    except ValueError:
        return None
    if end < start:
        return None
    return start.isoformat(), end.isoformat()


def extract_skt_newsroom_seongsu_candidates(
    payload: Any,
    source: dict[str, Any],
    now: datetime,
) -> list[dict[str, Any]]:
    """Build an internal-only review queue from SKT's public newsroom API.

    The newsroom permits access in robots.txt, but its usage guide restricts
    commercial reuse.  Consequently this parser stores only minimal discovery
    metadata and marks every row non-publishable pending provider permission.
    """
    if not isinstance(payload, list):
        return []

    location_terms = source.get("locationTerms")
    if not isinstance(location_terms, list):
        location_terms = ["T 팩토리 성수", "T팩토리 성수", "성수동"]
    topic_terms = source.get("topicTerms")
    if not isinstance(topic_terms, list):
        topic_terms = ["팝업", "전시", "체험", "프로모션", "미니 포레스트"]
    normalized_locations = [str(term).casefold() for term in location_terms if text_or_none(term)]
    normalized_topics = [str(term).casefold() for term in topic_terms if text_or_none(term)]
    today = (utc_now(now) + timedelta(hours=9)).date()
    active_window_days = positive_int(source.get("activeWindowDays"), 120, minimum=1, maximum=365)
    active_through = today + timedelta(days=active_window_days)
    official_source_url = allowlisted_http_url(source.get("officialSourceUrl"), source)
    usage_policy_url = allowlisted_http_url(source.get("usagePolicyUrl"), source)
    robots_url = allowlisted_http_url(source.get("robotsUrl"), source)
    address = text_or_none(source.get("address")) or ""
    latitude = parse_number(source.get("latitude"))
    longitude = parse_number(source.get("longitude"))
    if latitude is None or longitude is None or not official_source_url:
        return []

    candidates: list[dict[str, Any]] = []
    for post in payload:
        if not isinstance(post, dict):
            continue
        rendered_title = post.get("title") if isinstance(post.get("title"), dict) else {}
        rendered_excerpt = post.get("excerpt") if isinstance(post.get("excerpt"), dict) else {}
        rendered_content = post.get("content") if isinstance(post.get("content"), dict) else {}
        title = BeautifulSoup(str(rendered_title.get("rendered") or ""), "html.parser").get_text(" ", strip=True)
        html = " ".join(
            (
                str(rendered_excerpt.get("rendered") or ""),
                str(rendered_content.get("rendered") or ""),
            )
        )
        article_text = BeautifulSoup(html, "html.parser").get_text(" ", strip=True)
        searchable = f"{title} {article_text}".casefold()
        if normalized_locations and not any(term in searchable for term in normalized_locations):
            continue
        if normalized_topics and not any(term in searchable for term in normalized_topics):
            continue

        published_text = text_or_none(post.get("date")) or ""
        try:
            reference_date = date.fromisoformat(published_text[:10])
        except ValueError:
            reference_date = today
        dates = extract_korean_date_range(article_text, reference_date)
        if not dates:
            continue
        start_date, end_date = dates
        start = date.fromisoformat(start_date)
        end = date.fromisoformat(end_date)
        if end < today or start > active_through:
            continue

        detail_url = allowlisted_http_url(post.get("link"), source)
        content_id = text_or_none(post.get("id"))
        if not title or not detail_url or not content_id:
            continue
        fingerprint_payload = {
            "sourceId": source.get("id"),
            "sourceContentId": content_id,
            "title": title,
            "startDate": start_date,
            "endDate": end_date,
            "detailUrl": detail_url,
            "address": address,
            "latitude": latitude,
            "longitude": longitude,
        }
        digest = hashlib.sha256(
            json.dumps(fingerprint_payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
                "utf-8"
            )
        ).hexdigest()
        candidates.append(
            {
                "id": f"popup-review-{digest[:16]}",
                "candidateDigest": digest,
                "title": title,
                "kind": text_or_none(source.get("kind")) or "popup",
                "period": f"{start_date}-{end_date}",
                "startDate": start_date,
                "endDate": end_date,
                "latitude": latitude,
                "longitude": longitude,
                "address": address,
                "source": text_or_none(source.get("source")) or "SKT_NEWSROOM_DISCOVERY",
                "sourceId": text_or_none(source.get("id")),
                "sourceType": "discovery",
                "sourceUrl": official_source_url,
                "sourceContentId": content_id,
                "detailUrl": detail_url,
                "publishedAt": published_text or None,
                "modifiedAt": text_or_none(post.get("modified")),
                "discoveredAt": iso_timestamp(now),
                "collectionMode": "DISCOVERY_ONLY",
                "verificationStatus": "PENDING_RIGHTS_REVIEW",
                "publishable": False,
                "rightsStatus": text_or_none(source.get("rightsStatus"))
                or "PROVIDER_PERMISSION_REQUIRED",
                "publicationPolicy": "review_required",
                "usagePolicyUrl": usage_policy_url,
                "robotsUrl": robots_url,
                "blockingReason": (
                    "SKT newsroom usage terms restrict commercial reuse; obtain written provider permission "
                    "and re-verify dates/venue before publication."
                ),
            }
        )

    return candidates


def extract_discovery_candidates(
    response: Any,
    source: dict[str, Any],
    now: datetime,
) -> list[dict[str, Any]]:
    if source.get("parser") != "skt_newsroom_seongsu":
        return []
    try:
        payload = response.json()
    except ValueError:
        return []
    return extract_skt_newsroom_seongsu_candidates(payload, source, now)


def extract_shinsegae_shopping_records(html: str, source: dict[str, Any]) -> list[dict[str, Any]]:
    """Read the official Shinsegae shopping payload embedded in the page.

    The public shopping list exposes a JSON object as ``g_shoppingInfo``.  We
    deliberately keep this parser source-specific: only the configured branch
    is accepted and only rows explicitly identified as popups are published.
    Branch coordinates and address come from that branch's official store page
    and are pinned in configuration, rather than inferred from free-form text.
    """
    assignment = re.search(r"\bvar\s+g_shoppingInfo\s*=\s*", html)
    if assignment is None:
        return []

    try:
        payload, _ = json.JSONDecoder().raw_decode(html[assignment.end() :])
    except (json.JSONDecodeError, TypeError):
        return []

    rows = payload.get("page") if isinstance(payload, dict) else None
    if not isinstance(rows, list):
        return []

    configured_store = text_or_none(source.get("storeCode"))
    configured_keywords = source.get("popupKeywords")
    if isinstance(configured_keywords, list):
        keywords = [str(keyword).casefold() for keyword in configured_keywords if text_or_none(keyword)]
    else:
        keywords = ["팝업", "popup", "pop-up"]

    official_url = safe_http_url(source.get("officialSourceUrl"))
    records: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        if configured_store and text_or_none(row.get("storeCd")) != configured_store:
            continue

        searchable = " ".join(
            str(row.get(key) or "")
            for key in ("title1", "genreNm", "badge1", "badge2", "content1")
        ).casefold()
        if not keywords or not any(keyword in searchable for keyword in keywords):
            continue

        raw_title = text_or_none(row.get("title1"))
        title = BeautifulSoup(raw_title, "html.parser").get_text(" ", strip=True) if raw_title else None
        image_path = text_or_none(row.get("imgUrl1") or row.get("imgUrl2"))
        image_url = urljoin(official_url, image_path) if official_url and image_path else None
        displayed_dates = extract_explicit_period_dates(row.get("expDt"))
        records.append(
            {
                "id": text_or_none(row.get("id")),
                "title": title,
                "kind": "popup",
                "period": text_or_none(row.get("expDt")),
                "startDate": displayed_dates[0] if displayed_dates else row.get("startDt"),
                "endDate": displayed_dates[1] if displayed_dates else row.get("endDt"),
                "address": source.get("address"),
                "latitude": source.get("latitude"),
                "longitude": source.get("longitude"),
                "imageUrl": image_url,
                "url": official_url,
                "sourceContentId": text_or_none(row.get("id")),
            }
        )

    return records


def looks_like_event_candidate(value: dict[str, Any]) -> bool:
    has_title = text_or_none(read_first(value, EVENT_TITLE_KEYS)) is not None
    has_coordinates = first_nested(value, LATITUDE_KEYS) is not None and first_nested(value, LONGITUDE_KEYS) is not None
    has_date = any(value.get(key) for key in START_DATE_KEYS + END_DATE_KEYS + PERIOD_KEYS)
    return has_title and (has_coordinates or has_date)


def extract_embedded_json_records(soup: BeautifulSoup, source: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for script in soup.find_all("script"):
        script_id = text_or_none(script.get("id")) or ""
        script_type = text_or_none(script.get("type")) or ""
        if script_id != "__NEXT_DATA__" and "json" not in script_type:
            continue

        try:
            payload = json.loads(script.string or script.get_text() or "")
        except json.JSONDecodeError:
            continue

        for candidate in walk_dicts(payload):
            if not looks_like_event_candidate(candidate):
                continue
            records.append(
                {
                    "title": read_first(candidate, EVENT_TITLE_KEYS),
                    "kind": candidate.get("kind") or candidate.get("category") or candidate.get("CODENAME"),
                    "period": read_first(candidate, PERIOD_KEYS),
                    "startDate": candidate.get("startDate") or candidate.get("eventStartDate"),
                    "endDate": candidate.get("endDate") or candidate.get("eventEndDate"),
                    "address": first_nested(candidate, ADDRESS_KEYS),
                    "latitude": first_nested(candidate, LATITUDE_KEYS),
                    "longitude": first_nested(candidate, LONGITUDE_KEYS),
                    "imageUrl": first_nested(candidate, IMAGE_KEYS),
                    "url": read_first(candidate, URL_KEYS) or source.get("url"),
                    "sourceContentId": read_first(candidate, CONTENT_ID_KEYS),
                }
            )

    return records


def extract_json_payload_records(payload: Any, source: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    candidates: list[dict[str, Any]]
    if source.get("sourceType") == "seoul_culture_api" and isinstance(payload, dict):
        container = payload.get("culturalEventInfo")
        if not isinstance(container, dict):
            container = payload
        rows = container.get("row") or container.get("ROW")
        candidates = [candidate for candidate in rows if isinstance(candidate, dict)] if isinstance(rows, list) else []
    else:
        candidates = walk_dicts(payload)

    for candidate in candidates:
        if not looks_like_event_candidate(candidate):
            continue
        records.append(
            {
                "title": read_first(candidate, EVENT_TITLE_KEYS),
                "kind": candidate.get("kind") or candidate.get("category") or candidate.get("CODENAME"),
                "period": read_first(candidate, PERIOD_KEYS),
                "startDate": read_first(candidate, START_DATE_KEYS),
                "endDate": read_first(candidate, END_DATE_KEYS),
                "address": first_nested(candidate, ADDRESS_KEYS),
                "latitude": first_nested(candidate, LATITUDE_KEYS),
                "longitude": first_nested(candidate, LONGITUDE_KEYS),
                "imageUrl": first_nested(candidate, IMAGE_KEYS),
                "url": read_first(candidate, URL_KEYS) or source.get("publicUrl"),
                "sourceContentId": read_first(candidate, CONTENT_ID_KEYS),
            }
        )

    return records


def extract_feed_records(soup: BeautifulSoup, source: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    entries = soup.find_all(["item", "entry"])

    for entry in entries:
        title = entry.find("title")
        link = entry.find("link")
        start_date = (
            entry.find("startDate")
            or entry.find("eventStartDate")
            or entry.find("start")
            or entry.find("pubDate")
            or entry.find("updated")
            or entry.find("published")
        )
        end_date = entry.find("endDate") or entry.find("eventEndDate") or entry.find("end")
        description = entry.find("description") or entry.find("summary")
        image = entry.find("enclosure", attrs={"type": re.compile(r"image/", re.IGNORECASE)})

        latitude = (
            entry.find("geo:lat")
            or entry.find("lat")
            or entry.find("latitude")
            or entry.find("mapy")
        )
        longitude = (
            entry.find("geo:long")
            or entry.find("geo:lon")
            or entry.find("lng")
            or entry.find("lon")
            or entry.find("longitude")
            or entry.find("mapx")
        )
        point = entry.find("georss:point") or entry.find("point")
        if (latitude is None or longitude is None) and point is not None:
            parts = point.get_text(" ", strip=True).split()
            if len(parts) >= 2:
                latitude_value: Any = parts[0]
                longitude_value: Any = parts[1]
            else:
                latitude_value = None
                longitude_value = None
        else:
            latitude_value = latitude.get_text(strip=True) if latitude else None
            longitude_value = longitude.get_text(strip=True) if longitude else None

        link_value = None
        if link is not None:
            link_value = link.get("href") or link.get_text(strip=True)

        records.append(
            {
                "title": title.get_text(strip=True) if title else None,
                "period": start_date.get_text(strip=True) if start_date else None,
                "startDate": start_date.get_text(strip=True) if start_date else None,
                "endDate": end_date.get_text(strip=True) if end_date else None,
                "address": text_or_none(source.get("address"))
                or (description.get_text(" ", strip=True) if description else None),
                "latitude": latitude_value,
                "longitude": longitude_value,
                "imageUrl": image.get("url") if image else None,
                "url": link_value or source.get("publicUrl"),
            }
        )

    return records


def extract_xml_feed_records(xml_text: str, source: dict[str, Any]) -> list[dict[str, Any]]:
    """Extract RSS/Atom records without requiring BeautifulSoup's optional XML parser."""
    root = ElementTree.fromstring(xml_text)
    entries = [element for element in root.iter() if xml_local_name(element.tag) in {"item", "entry"}]
    records: list[dict[str, Any]] = []

    for entry in entries:
        children = list(entry.iter())

        def first(*names: str) -> ElementTree.Element | None:
            wanted = {name.lower() for name in names}
            return next((element for element in children if xml_local_name(element.tag) in wanted), None)

        title = first("title")
        link = first("link")
        start_date = first("startDate", "eventStartDate", "start", "pubDate", "updated", "published")
        end_date = first("endDate", "eventEndDate", "end")
        description = first("description", "summary")
        image = next(
            (
                element
                for element in children
                if xml_local_name(element.tag) == "enclosure"
                and str(element.attrib.get("type", "")).lower().startswith("image/")
            ),
            None,
        )

        latitude = first("lat", "latitude", "mapy")
        longitude = first("long", "lon", "lng", "longitude", "mapx")
        point = first("point")
        if (latitude is None or longitude is None) and point is not None:
            point_parts = xml_text_value(point).split()
            latitude_value: Any = point_parts[0] if len(point_parts) >= 2 else None
            longitude_value: Any = point_parts[1] if len(point_parts) >= 2 else None
        else:
            latitude_value = xml_text_value(latitude) or None
            longitude_value = xml_text_value(longitude) or None

        link_value = None
        if link is not None:
            link_value = text_or_none(link.attrib.get("href")) or text_or_none(xml_text_value(link))

        records.append(
            {
                "title": text_or_none(xml_text_value(title)),
                "period": text_or_none(xml_text_value(start_date)),
                "startDate": text_or_none(xml_text_value(start_date)),
                "endDate": text_or_none(xml_text_value(end_date)),
                "address": text_or_none(source.get("address")) or text_or_none(xml_text_value(description)),
                "latitude": latitude_value,
                "longitude": longitude_value,
                "imageUrl": text_or_none(image.attrib.get("url")) if image is not None else None,
                "url": link_value or source.get("publicUrl"),
            }
        )

    return records


def xml_local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].split(":", 1)[-1].lower()


def xml_text_value(element: ElementTree.Element | None) -> str:
    return " ".join("".join(element.itertext()).split()) if element is not None else ""


def extract_open_graph_candidate(soup: BeautifulSoup, source: dict[str, Any]) -> dict[str, Any] | None:
    def meta(name: str) -> str | None:
        tag = soup.find("meta", property=name) or soup.find("meta", attrs={"name": name})
        if not tag:
            return None
        return text_or_none(tag.get("content"))

    title = meta("og:title") or (soup.title.string.strip() if soup.title and soup.title.string else None)
    if not title:
        return None

    keyword = text_or_none(source.get("keyword"))
    if keyword and not re.search(re.escape(keyword), title, flags=re.IGNORECASE):
        body_text = soup.get_text(" ", strip=True)[:5000]
        if not re.search(re.escape(keyword), body_text, flags=re.IGNORECASE):
            return None

    return {
        "title": title,
        "address": source.get("address"),
        "latitude": source.get("latitude"),
        "longitude": source.get("longitude"),
        "imageUrl": meta("og:image"),
        "url": meta("og:url") or source.get("url"),
    }


def extract_response_records(response: Any, source: dict[str, Any]) -> list[dict[str, Any]]:
    content_type = str(response.headers.get("content-type", ""))
    raw_records: list[dict[str, Any]] = []

    if source.get("parser") == "shinsegae_shopping":
        raw_records = extract_shinsegae_shopping_records(response.text, source)

    if not raw_records and "json" in content_type.lower():
        try:
            raw_records = extract_json_payload_records(response.json(), source)
        except ValueError:
            raw_records = []

    if not raw_records and (
        "xml" in content_type.lower()
        or "rss" in content_type.lower()
        or "atom" in content_type.lower()
    ):
        try:
            raw_records = extract_xml_feed_records(response.text, source)
        except ElementTree.ParseError:
            feed_soup = BeautifulSoup(response.text, "html.parser")
            raw_records = extract_feed_records(feed_soup, source)

    soup = BeautifulSoup(response.text, "html.parser")
    if not raw_records:
        raw_records = extract_json_ld_events(soup, source)
    if not raw_records:
        raw_records = extract_embedded_json_records(soup, source)
    if not raw_records:
        og_record = extract_open_graph_candidate(soup, source)
        raw_records = [og_record] if og_record else []

    normalized = [normalize_record(raw, source) for raw in raw_records if raw]
    return [record for record in normalized if record and validate_record(record)]


def fetch_source(
    source: dict[str, Any],
    timeout: int,
    cached_state: dict[str, Any] | None = None,
    *,
    request_get: Callable[..., Any] = requests.get,
    sleep_fn: Callable[[float], None] = time.sleep,
    now: datetime | None = None,
) -> SourceFetchResult:
    current_time = utc_now(now)
    cached = dict(cached_state or {})
    source_id = text_or_none(source.get("id")) or "unknown-source"
    source_type = text_or_none(source.get("sourceType")) or "unknown"
    state = source_state_base(source, cached, current_time)
    cached_record_count = positive_int(cached.get("recordCount"), 0, minimum=0)

    definition_error = validate_source_definition(source)
    if definition_error:
        state.update(
            {
                "lastStatus": "invalid_config",
                "lastError": definition_error,
                "nextRunAt": scheduled_next_run(source, current_time),
            }
        )
        return SourceFetchResult(source_id, source_type, "invalid_config", [], state, message=definition_error)

    if cached.get("disabled") is True and source.get("resetDisabled") is not True:
        state["lastStatus"] = "disabled_previous"
        state["nextRunAt"] = None
        return SourceFetchResult(source_id, source_type, "disabled_previous", [], state)

    pagination = source_pagination(source)
    headers = {
        "User-Agent": DEFAULT_USER_AGENT,
        "Accept": "application/json, application/rss+xml, application/atom+xml, text/html;q=0.9, */*;q=0.1",
    }
    if not pagination and text_or_none(cached.get("etag")):
        headers["If-None-Match"] = str(cached["etag"])
    if not pagination and text_or_none(cached.get("lastModified")):
        headers["If-Modified-Since"] = str(cached["lastModified"])

    max_attempts = positive_int(source.get("maxAttempts"), 3, minimum=1, maximum=5)
    base_backoff = float(positive_int(source.get("baseBackoffSeconds"), 2, minimum=1, maximum=60))
    max_backoff = float(positive_int(source.get("maxBackoffSeconds"), 300, minimum=1, maximum=3_600))
    max_inline_retry = float(
        positive_int(source.get("maxInlineRetrySeconds"), 30, minimum=1, maximum=60)
    )
    first_page_url = render_source_url(
        source,
        current_time,
        page_start=1 if pagination else None,
        page_end=pagination["pageSize"] if pagination else None,
    )
    failure_count = positive_int(cached.get("failureCount"), 0, minimum=0)
    last_delay = base_backoff

    for attempt in range(max_attempts):
        try:
            response = request_get(
                first_page_url,
                headers=headers,
                timeout=timeout,
                allow_redirects=False,
            )
        except requests.RequestException as error:
            safe_error = f"{error.__class__.__name__}: request failed"
            failure_count += 1
            last_delay = min(base_backoff * (2**attempt), max_backoff)
            if attempt + 1 < max_attempts and last_delay <= max_inline_retry:
                sleep_fn(last_delay)
                continue
            state.update(
                {
                    "lastStatus": "error",
                    "lastError": safe_error,
                    "failureCount": failure_count,
                    "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                }
            )
            return SourceFetchResult(source_id, source_type, "error", [], state, message=safe_error)

        status_code = int(response.status_code)
        state["lastHttpStatus"] = status_code

        if status_code in {401, 403}:
            state.update(
                {
                    "lastStatus": "disabled_auth",
                    "lastError": f"HTTP {status_code}",
                    "failureCount": failure_count + 1,
                    "disabled": True,
                    "disabledReason": f"http_{status_code}",
                    "nextRunAt": None,
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                "disabled_auth",
                [],
                state,
                http_status=status_code,
            )

        if status_code in TRANSIENT_HTTP_STATUSES:
            failure_count += 1
            exponential_delay = base_backoff * (2**attempt)
            last_delay = retry_after_seconds(response.headers, current_time, exponential_delay, max_backoff)
            if attempt + 1 < max_attempts and last_delay <= max_inline_retry:
                sleep_fn(last_delay)
                continue
            state.update(
                {
                    "lastStatus": "backoff",
                    "lastError": f"HTTP {status_code}",
                    "failureCount": failure_count,
                    "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                "backoff",
                [],
                state,
                http_status=status_code,
            )

        if status_code == 304:
            if pagination:
                message = "unexpected HTTP 304 for paginated source"
                state.update(
                    {
                        "lastStatus": "error",
                        "lastError": message,
                        "failureCount": failure_count + 1,
                        "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                    }
                )
                return SourceFetchResult(
                    source_id,
                    source_type,
                    "error",
                    [],
                    state,
                    http_status=status_code,
                    message=message,
                )
            state.update(
                {
                    "etag": text_or_none(response.headers.get("ETag")) or cached.get("etag"),
                    "lastModified": text_or_none(response.headers.get("Last-Modified")) or cached.get("lastModified"),
                    "lastStatus": "not_modified",
                    "lastSuccessAt": iso_timestamp(current_time),
                    "lastError": None,
                    "failureCount": 0,
                    "nextRunAt": scheduled_next_run(source, current_time),
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                "not_modified",
                [],
                state,
                refresh_retained_records=source_type != "discovery" and cached_record_count > 0,
                http_status=status_code,
            )

        if 300 <= status_code < 400:
            message = f"unexpected HTTP redirect {status_code}"
            state.update(
                {
                    "lastStatus": "error",
                    "lastError": message,
                    "failureCount": failure_count + 1,
                    "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                "error",
                [],
                state,
                http_status=status_code,
                message=message,
            )

        try:
            response.raise_for_status()
        except requests.RequestException as error:
            safe_error = f"HTTP {status_code} ({error.__class__.__name__})"
            failure_count += 1
            last_delay = min(base_backoff * (2**attempt), max_backoff)
            state.update(
                {
                    "lastStatus": "error",
                    "lastError": safe_error,
                    "failureCount": failure_count,
                    "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                "error",
                [],
                state,
                http_status=status_code,
                message=safe_error,
            )

        responses = [response]
        pagination_state: dict[str, int] = {}
        if pagination:
            total_available, first_page_rows, page_contract_error = seoul_open_api_page_contract(response)
            if page_contract_error or total_available is None or first_page_rows is None:
                message = page_contract_error or "official pagination response is invalid"
                state.update(
                    {
                        "lastStatus": "error",
                        "lastError": message,
                        "failureCount": failure_count + 1,
                        "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                    }
                )
                return SourceFetchResult(
                    source_id,
                    source_type,
                    "error",
                    [],
                    state,
                    http_status=status_code,
                    message=message,
                )

            page_size = pagination["pageSize"]
            page_count = max(1, (total_available + page_size - 1) // page_size)
            pagination_state = {
                "totalAvailable": total_available,
                "pageCount": page_count,
                "pageSize": page_size,
            }
            if page_count > pagination["maxPages"]:
                message = (
                    "pagination limit exceeded: "
                    f"{page_count} pages required, {pagination['maxPages']} allowed"
                )
                state.update(
                    {
                        **pagination_state,
                        "lastStatus": "error",
                        "lastError": message,
                        "failureCount": failure_count + 1,
                        "nextRunAt": scheduled_next_run(source, current_time),
                    }
                )
                return SourceFetchResult(
                    source_id,
                    source_type,
                    "error",
                    [],
                    state,
                    http_status=status_code,
                    message=message,
                )

            expected_first_page_rows = min(page_size, total_available)
            if first_page_rows != expected_first_page_rows:
                message = (
                    "official pagination row count mismatch on page 1: "
                    f"expected {expected_first_page_rows}, got {first_page_rows}"
                )
                state.update(
                    {
                        **pagination_state,
                        "lastStatus": "error",
                        "lastError": message,
                        "failureCount": failure_count + 1,
                        "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
                    }
                )
                return SourceFetchResult(
                    source_id,
                    source_type,
                    "error",
                    [],
                    state,
                    http_status=status_code,
                    message=message,
                )

            page_headers = {
                key: value
                for key, value in headers.items()
                if key not in {"If-None-Match", "If-Modified-Since"}
            }
            for page_index in range(1, page_count):
                page_start = page_index * page_size + 1
                page_end = min((page_index + 1) * page_size, total_available)
                page_response, page_error = fetch_additional_page(
                    render_source_url(
                        source,
                        current_time,
                        page_start=page_start,
                        page_end=page_end,
                    ),
                    headers=page_headers,
                    timeout=timeout,
                    request_get=request_get,
                    sleep_fn=sleep_fn,
                    now=current_time,
                    max_attempts=max_attempts,
                    base_backoff=base_backoff,
                    max_backoff=max_backoff,
                    max_inline_retry=max_inline_retry,
                )
                if page_error:
                    page_status = str(page_error["status"])
                    page_http_status = page_error.get("httpStatus")
                    page_delay = page_error.get("nextDelay")
                    state.update(
                        {
                            **pagination_state,
                            "lastStatus": page_status,
                            "lastError": page_error["message"],
                            "failureCount": failure_count + 1,
                            "lastHttpStatus": page_http_status,
                            "nextRunAt": (
                                None
                                if page_status == "disabled_auth"
                                else iso_timestamp(
                                    current_time
                                    + timedelta(seconds=float(page_delay or base_backoff))
                                )
                            ),
                        }
                    )
                    if page_status == "disabled_auth":
                        state["disabled"] = True
                        state["disabledReason"] = f"http_{page_http_status}"
                    return SourceFetchResult(
                        source_id,
                        source_type,
                        page_status,
                        [],
                        state,
                        http_status=page_http_status,
                        message=str(page_error["message"]),
                    )

                page_total, page_rows, page_contract_error = seoul_open_api_page_contract(page_response)
                expected_page_rows = page_end - page_start + 1
                if page_contract_error:
                    message = f"page {page_index + 1}: {page_contract_error}"
                elif page_total != total_available:
                    message = (
                        f"official pagination total count drift on page {page_index + 1}: "
                        f"expected {total_available}, got {page_total}"
                    )
                elif page_rows != expected_page_rows:
                    message = (
                        f"official pagination row count mismatch on page {page_index + 1}: "
                        f"expected {expected_page_rows}, got {page_rows}"
                    )
                else:
                    message = None
                if message:
                    state.update(
                        {
                            **pagination_state,
                            "lastStatus": "error",
                            "lastError": message,
                            "failureCount": failure_count + 1,
                            "lastHttpStatus": int(page_response.status_code),
                            "nextRunAt": iso_timestamp(current_time + timedelta(seconds=base_backoff)),
                        }
                    )
                    return SourceFetchResult(
                        source_id,
                        source_type,
                        "error",
                        [],
                        state,
                        http_status=int(page_response.status_code),
                        message=message,
                    )
                responses.append(page_response)

        body_hash = response_collection_hash(responses)
        etag = text_or_none(response.headers.get("ETag"))
        last_modified = text_or_none(response.headers.get("Last-Modified"))
        if not etag and not last_modified and cached.get("bodyHash") == body_hash:
            state.update(
                {
                    **pagination_state,
                    "bodyHash": body_hash,
                    "lastStatus": "unchanged",
                    "lastSuccessAt": iso_timestamp(current_time),
                    "lastError": None,
                    "failureCount": 0,
                    "nextRunAt": scheduled_next_run(source, current_time),
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                "unchanged",
                [],
                state,
                persist_records=source_type != "discovery",
                refresh_retained_records=source_type != "discovery" and cached_record_count > 0,
                http_status=status_code,
            )

        if source_type == "discovery":
            candidates = extract_discovery_candidates(response, source, current_time)
            fetch_status = "discovery_ready" if candidates else "discovery_empty"
            state.update(
                {
                    "etag": etag,
                    "lastModified": last_modified,
                    "bodyHash": body_hash,
                    "lastStatus": fetch_status,
                    "lastSuccessAt": iso_timestamp(current_time),
                    "lastError": None,
                    "failureCount": 0,
                    "disabled": False,
                    "disabledReason": None,
                    "recordCount": 0,
                    "candidateCount": len(candidates),
                    "nextRunAt": scheduled_next_run(source, current_time),
                }
            )
            return SourceFetchResult(
                source_id,
                source_type,
                fetch_status,
                [],
                state,
                candidates=candidates,
                persist_records=False,
                replace_candidates=True,
                http_status=status_code,
            )

        records = [
            record
            for page_response in responses
            for record in extract_response_records(page_response, source)
            if is_verifiable_record(record, as_of=current_time) and record_matches_source_filters(record, source)
        ]
        fetch_status = "success" if records else "empty"
        # An empty result preserves the last-good output, so it must not commit
        # validators for a representation that was never published. Otherwise
        # a later 304/body-hash match could incorrectly refresh stale records.
        committed_etag = etag if records else cached.get("etag")
        committed_last_modified = last_modified if records else cached.get("lastModified")
        committed_body_hash = body_hash if records else cached.get("bodyHash")
        state.update(
            {
                **pagination_state,
                "etag": committed_etag,
                "lastModified": committed_last_modified,
                "bodyHash": committed_body_hash,
                "lastStatus": fetch_status,
                "lastSuccessAt": iso_timestamp(current_time),
                "lastError": None,
                "failureCount": 0,
                "disabled": False,
                "disabledReason": None,
                "recordCount": len(records),
                "nextRunAt": scheduled_next_run(source, current_time),
            }
        )
        return SourceFetchResult(
            source_id,
            source_type,
            fetch_status,
            records,
            state,
            persist_records=True,
            replace_records=bool(records),
            http_status=status_code,
        )

    state.update(
        {
            "lastStatus": "error",
            "lastError": "retry loop exhausted",
            "failureCount": failure_count,
            "nextRunAt": iso_timestamp(current_time + timedelta(seconds=last_delay)),
        }
    )
    return SourceFetchResult(source_id, source_type, "error", [], state, message="retry loop exhausted")


def disabled_source_result(
    source: dict[str, Any],
    cached_state: dict[str, Any],
    now: datetime,
    status: str,
    message: str,
) -> SourceFetchResult:
    source_id = text_or_none(source.get("id")) or "unknown-source"
    source_type = text_or_none(source.get("sourceType")) or "unknown"
    state = source_state_base(source, cached_state, now)
    state.update(
        {
            "lastStatus": status,
            "lastError": message,
            "disabledReason": message if status.startswith("disabled") else cached_state.get("disabledReason"),
            "nextRunAt": (
                None
                if status in {"disabled_config", "disabled_missing_env", "disabled_sample"}
                else scheduled_next_run(source, now)
            ),
            "recordCount": cached_state.get("recordCount", 0),
        }
    )
    return SourceFetchResult(source_id, source_type, status, [], state, message=message)


def collect_due_sources(
    config: dict[str, Any],
    timeout: int,
    state: dict[str, Any] | None = None,
    *,
    request_get: Callable[..., Any] = requests.get,
    sleep_fn: Callable[[float], None] = time.sleep,
    now: datetime | None = None,
    force: bool = False,
) -> CollectionBatch:
    current_time = utc_now(now)
    previous_state = state if isinstance(state, dict) else {}
    previous_sources = previous_state.get("sources") if isinstance(previous_state.get("sources"), dict) else {}
    next_sources = dict(previous_sources)
    results: list[SourceFetchResult] = []
    records: list[dict[str, Any]] = []
    candidates: list[dict[str, Any]] = []
    allow_sample = config.get("allowSampleFallback") is True or os.environ.get(
        "POPUP_TRENDS_ALLOW_SAMPLE_FALLBACK", ""
    ).lower() == "true"
    allow_manual = config.get("allowManualFallback") is True or os.environ.get(
        "POPUP_TRENDS_ALLOW_MANUAL_FALLBACK", ""
    ).lower() == "true"

    for raw_source in config.get("sources", []):
        if not isinstance(raw_source, dict):
            continue
        source = raw_source
        source_id = text_or_none(source.get("id")) or "unknown-source"
        cached = previous_sources.get(source_id) if isinstance(previous_sources.get(source_id), dict) else {}

        if source.get("enabled") is False:
            disabled_reason = text_or_none(source.get("disabledReason")) or "source is disabled"
            result = disabled_source_result(source, cached, current_time, "disabled_config", disabled_reason)
        else:
            definition_error = validate_source_definition(source)
            if definition_error:
                result = disabled_source_result(source, cached, current_time, "invalid_config", definition_error)
            elif not force and not source_is_due(source, cached, current_time):
                result = SourceFetchResult(
                    source_id,
                    str(source.get("sourceType")),
                    "not_due",
                    [],
                    dict(cached),
                    persist_records=False,
                )
            else:
                required_env = source.get("requiredEnv") if isinstance(source.get("requiredEnv"), list) else []
                missing_env = [name for name in required_env if not text_or_none(os.environ.get(str(name)))]
                source_url = text_or_none(source.get("url")) or ""
                sample_pattern = re.compile(r"(?:^|[/=?&])sample(?:$|[/&#?])", flags=re.IGNORECASE)
                source_type = source.get("sourceType")
                if missing_env:
                    result = disabled_source_result(
                        source,
                        cached,
                        current_time,
                        "disabled_missing_env",
                        f"missing required environment variables: {', '.join(map(str, missing_env))}",
                    )
                elif (source.get("sampleFallback") is True or sample_pattern.search(source_url)) and not allow_sample:
                    result = disabled_source_result(
                        source,
                        cached,
                        current_time,
                        "disabled_sample",
                        "sample fallback is disabled",
                    )
                elif source_type == "manual" and not allow_manual:
                    result = disabled_source_result(
                        source,
                        cached,
                        current_time,
                        "disabled_manual_fallback",
                        "manual fallback is disabled",
                    )
                elif source_type == "manual":
                    record = normalize_record(source, source)
                    manual_records = [record] if record and validate_record(record) else []
                    manual_state = source_state_base(source, cached, current_time)
                    manual_state.update(
                        {
                            "lastStatus": "manual_fallback",
                            "lastSuccessAt": iso_timestamp(current_time),
                            "lastError": None,
                            "recordCount": len(manual_records),
                            "nextRunAt": scheduled_next_run(source, current_time),
                        }
                    )
                    result = SourceFetchResult(
                        source_id,
                        "manual",
                        "manual_fallback",
                        manual_records,
                        manual_state,
                        replace_records=bool(manual_records),
                    )
                else:
                    result = fetch_source(
                        source,
                        timeout,
                        cached,
                        request_get=request_get,
                        sleep_fn=sleep_fn,
                        now=current_time,
                    )

        results.append(result)
        if result.state:
            next_sources[source_id] = result.state
        if result.persist_records:
            records.extend(result.records)
        candidates.extend(result.candidates)

    deduped_records: list[dict[str, Any]] = []
    seen: set[str] = set()
    for record in records:
        key = dedupe_key(record)
        if key in seen:
            continue
        seen.add(key)
        deduped_records.append(record)

    next_state = {
        "version": 1,
        "updatedAt": iso_timestamp(current_time),
        "sources": next_sources,
    }
    deduped_candidates: list[dict[str, Any]] = []
    seen_candidates: set[str] = set()
    for candidate in candidates:
        candidate_key = text_or_none(candidate.get("candidateDigest")) or text_or_none(candidate.get("id"))
        if not candidate_key or candidate_key in seen_candidates:
            continue
        seen_candidates.add(candidate_key)
        deduped_candidates.append(candidate)

    return CollectionBatch(
        records=deduped_records,
        results=results,
        state=next_state,
        candidates=deduped_candidates,
    )


def collect_source(source: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
    if source.get("manual") is True:
        manual = normalize_record(source, source)
        return [manual] if manual else []

    url = text_or_none(source.get("url"))
    if not url:
        manual = normalize_record(source, source)
        return [manual] if manual else []

    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return []

    response = requests.get(url, headers={"User-Agent": DEFAULT_USER_AGENT}, timeout=timeout)
    response.raise_for_status()
    return extract_response_records(response, source)


def collect(config: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
    if config.get("version") == 1:
        return collect_due_sources(config, timeout, force=True).records

    collected: list[dict[str, Any]] = []
    seen: set[str] = set()

    for source in config.get("sources", []):
        if not isinstance(source, dict):
            continue
        try:
            records = collect_source(source, timeout)
        except requests.RequestException as error:
            print(f"skip {source.get('url')}: {error}")
            continue
        for record in records:
            if not validate_record(record):
                continue
            key = dedupe_key(record)
            if key in seen:
                continue
            seen.add(key)
            collected.append(record)

    return collected


def main() -> None:
    parser = argparse.ArgumentParser(description="Collect popup/event trend records for NugulMap Season 3.")
    parser.add_argument("--config", default=None)
    parser.add_argument("--output", default=None)
    parser.add_argument("--timeout", type=int, default=10)
    parser.add_argument("--min-records", type=int, default=1)
    args = parser.parse_args()

    config_path = Path(args.config) if args.config else DEFAULT_CONFIG_PATH
    output_path = Path(args.output) if args.output else DEFAULT_OUTPUT_PATH
    output_path.parent.mkdir(parents=True, exist_ok=True)

    records = collect(read_config(config_path), args.timeout)
    verified_records = [record for record in records if is_verifiable_record(record)]
    verified_count = len(verified_records)
    if verified_count < args.min_records:
        print(
            f"expected at least {args.min_records} verified network records; "
            f"got {verified_count}; preserving existing file",
            file=sys.stderr,
        )
        raise SystemExit(2)
    output_path.write_text(json.dumps(verified_records, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {len(verified_records)} verified popup trend records to {output_path}")


if __name__ == "__main__":
    main()
