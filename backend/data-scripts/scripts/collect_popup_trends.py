import argparse
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import requests
from bs4 import BeautifulSoup


DEFAULT_USER_AGENT = "NugulMapSeason2Bot/0.1 (+https://nugulmap.com)"
DATA_SCRIPTS_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG_PATH = DATA_SCRIPTS_ROOT / "config" / "popup_trend_sources.json"
DEFAULT_OUTPUT_PATH = DATA_SCRIPTS_ROOT / "data" / "popup-trends.json"
EVENT_TITLE_KEYS = ("title", "name", "eventTitle", "displayName", "TITLE")
LATITUDE_KEYS = ("latitude", "lat", "mapy", "y", "LAT")
LONGITUDE_KEYS = ("longitude", "lng", "lon", "mapx", "x", "LOT")
START_DATE_KEYS = ("startDate", "eventStartDate", "start_date", "start", "STRTDATE", "RGSTDATE")
END_DATE_KEYS = ("endDate", "eventEndDate", "end_date", "end", "END_DATE")
PERIOD_KEYS = ("period", "DATE", "date")
ADDRESS_KEYS = ("address", "addr1", "roadAddress", "locationName", "placeName", "PLACE", "GUNAME")
IMAGE_KEYS = ("imageUrl", "image", "firstimage", "thumbnail", "thumbnailUrl", "MAIN_IMG")
URL_KEYS = ("url", "link", "href", "contentUrl", "HMPG_ADDR", "ORG_LINK")
SEOUL_BOUNDS = {
    "min_lat": 37.40,
    "max_lat": 37.72,
    "min_lng": 126.76,
    "max_lng": 127.20,
}


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
        return match.group().replace(".", "-")

    match = re.search(r"\d{8}", text)
    if match:
        raw = match.group()
        return f"{raw[:4]}-{raw[4:6]}-{raw[6:8]}"

    return text


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


def normalize_record(raw: dict[str, Any], source: dict[str, Any]) -> dict[str, Any] | None:
    title = text_or_none(read_first(raw, EVENT_TITLE_KEYS))
    latitude = parse_number(first_nested(raw, LATITUDE_KEYS) or source.get("latitude"))
    longitude = parse_number(first_nested(raw, LONGITUDE_KEYS) or source.get("longitude"))
    source_url = text_or_none(read_first(raw, URL_KEYS) or source.get("url")) or ""
    image_url = read_image_url(first_nested(raw, IMAGE_KEYS))

    if not title or latitude is None or longitude is None:
        return None

    start_date = normalize_date_text(read_first(raw, START_DATE_KEYS))
    end_date = normalize_date_text(read_first(raw, END_DATE_KEYS))
    period = text_or_none(read_first(raw, PERIOD_KEYS))
    if not period and start_date:
        period = start_date if not end_date or end_date == start_date else f"{start_date}-{end_date}"

    return {
        "id": text_or_none(raw.get("id")) or stable_id(source_url, title),
        "title": title,
        "kind": text_or_none(raw.get("kind")) or text_or_none(source.get("kind")) or "popup",
        "period": period or text_or_none(source.get("period")) or "최근 후보",
        "startDate": start_date,
        "endDate": end_date,
        "latitude": latitude,
        "longitude": longitude,
        "address": text_or_none(first_nested(raw, ADDRESS_KEYS)) or text_or_none(source.get("address")) or "",
        "imageUrl": image_url,
        "source": text_or_none(raw.get("source")) or text_or_none(source.get("source")) or "CRAWLED_POPUP_TREND",
        "sourceContentId": source_url or stable_id("manual", title),
        "collectedAt": datetime.now(timezone.utc).isoformat(),
    }


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
                }
            )

    return records


def extract_json_payload_records(payload: Any, source: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for candidate in walk_dicts(payload):
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
                "url": read_first(candidate, URL_KEYS) or source.get("url"),
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
                "address": text_or_none(source.get("address")) or (description.get_text(" ", strip=True) if description else None),
                "latitude": latitude_value,
                "longitude": longitude_value,
                "imageUrl": image.get("url") if image else None,
                "url": link_value or source.get("url"),
            }
        )

    return records


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
    content_type = response.headers.get("content-type", "")
    raw_records: list[dict[str, Any]] = []

    if "json" in content_type.lower():
        try:
            raw_records = extract_json_payload_records(response.json(), source)
        except ValueError:
            raw_records = []

    if not raw_records and (
        "xml" in content_type.lower()
        or "rss" in content_type.lower()
        or "atom" in content_type.lower()
    ):
        feed_soup = BeautifulSoup(response.text, "xml")
        raw_records = extract_feed_records(feed_soup, source)

    soup = BeautifulSoup(response.text, "html.parser")
    if not raw_records:
        raw_records = extract_json_ld_events(soup, source)
    if not raw_records:
        raw_records = extract_embedded_json_records(soup, source)
    if not raw_records:
        og_record = extract_open_graph_candidate(soup, source)
        raw_records = [og_record] if og_record else []

    records = [normalize_record(raw, source) for raw in raw_records if raw]
    return [record for record in records if record and validate_record(record)]


def collect(config: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
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
    parser = argparse.ArgumentParser(description="Collect popup/event trend records for NugulMap Season 2.")
    parser.add_argument("--config", default=None)
    parser.add_argument("--output", default=None)
    parser.add_argument("--timeout", type=int, default=10)
    parser.add_argument("--min-records", type=int, default=0)
    args = parser.parse_args()

    config_path = Path(args.config) if args.config else DEFAULT_CONFIG_PATH
    output_path = Path(args.output) if args.output else DEFAULT_OUTPUT_PATH
    output_path.parent.mkdir(parents=True, exist_ok=True)

    records = collect(read_config(config_path), args.timeout)
    output_path.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {len(records)} popup trend records to {output_path}")
    if len(records) < args.min_records:
        print(f"expected at least {args.min_records} popup trend records", file=sys.stderr)
        raise SystemExit(2)


if __name__ == "__main__":
    main()
