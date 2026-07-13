"""Run the Season 3 popup/event trend collector once or on an interval."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any

from collect_popup_trends import (
    DEFAULT_CONFIG_PATH,
    DEFAULT_OUTPUT_PATH,
    DEFAULT_REVIEW_PATH,
    DEFAULT_STATE_PATH,
    DEFAULT_STATUS_PATH,
    CollectionBatch,
    allowlisted_http_url,
    collect_due_sources,
    dedupe_key,
    is_verifiable_record,
    parse_timestamp,
    read_config,
    record_matches_source_filters,
    source_schedule,
    validate_source_definition,
)


def positive_int(value: str, fallback: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return fallback
    return parsed if parsed > 0 else fallback


def nonnegative_int(value: str, fallback: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return fallback
    return parsed if parsed >= 0 else fallback


def read_json_file(path: Path, fallback: Any) -> Any:
    if not path.is_file():
        return fallback
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return fallback


def write_json_atomic(output_path: Path, payload: Any) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = output_path.with_suffix(f"{output_path.suffix}.tmp")
    temp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temp_path.replace(output_path)


def write_records_atomic(output_path: Path, records: list[dict]) -> None:
    write_json_atomic(output_path, records)


def merge_records(existing: list[dict], batch: CollectionBatch, config: dict | None = None) -> list[dict]:
    replace_source_ids = {result.source_id for result in batch.results if result.replace_records}
    refresh_source_times = {
        result.source_id: result.state.get("lastSuccessAt")
        for result in batch.results
        if result.refresh_retained_records and result.state.get("lastSuccessAt")
    }
    configured_source_ids = {result.source_id for result in batch.results}
    source_definitions = {
        str(source.get("id")): source
        for source in (config or {}).get("sources", [])
        if isinstance(source, dict) and source.get("id")
    }
    collection_as_of = parse_timestamp(batch.state.get("updatedAt"))

    def source_still_allows_publication(source_definition: dict | None, record: dict) -> bool:
        if not source_definition or source_definition.get("enabled") is False:
            return False
        if source_definition.get("persistRecords") is False:
            return False
        if validate_source_definition(source_definition) is not None:
            return False
        expected_values = {
            "source": source_definition.get("source"),
            "sourceType": source_definition.get("sourceType"),
            "sourceUrl": source_definition.get("officialSourceUrl"),
            "publicationPolicy": source_definition.get("publicationPolicy"),
            "attribution": source_definition.get("attribution"),
            "license": source_definition.get("license"),
            "licenseUrl": source_definition.get("licenseUrl"),
        }
        return all(record.get(field_name) == expected for field_name, expected in expected_values.items())

    def can_retain(record: dict) -> bool:
        source_id = record.get("sourceId")
        if not source_id or source_id not in configured_source_ids or source_id in replace_source_ids:
            return False
        if not is_verifiable_record(record, as_of=collection_as_of):
            return False

        source_definition = source_definitions.get(source_id)
        if not source_still_allows_publication(source_definition, record):
            return False
        if source_definition and (
            allowlisted_http_url(record.get("sourceUrl"), source_definition) is None
            or allowlisted_http_url(record.get("detailUrl"), source_definition) is None
            or not record_matches_source_filters(record, source_definition)
        ):
            return False
        return True

    retained = []
    for record in existing:
        if not isinstance(record, dict) or not can_retain(record):
            continue
        refreshed_at = refresh_source_times.get(record.get("sourceId"))
        retained.append({**record, "collectedAt": refreshed_at} if refreshed_at else record)

    merged: list[dict] = []
    seen: set[str] = set()
    # Fresh records win over stale copies when an old dataset did not yet have
    # sourceId provenance.
    for record in [*batch.records, *retained]:
        if not isinstance(record, dict):
            continue
        key = dedupe_key(record)
        if key in seen:
            continue
        seen.add(key)
        merged.append(record)
    return merged


def merge_candidate_queue(existing: Any, batch: CollectionBatch) -> dict[str, Any]:
    """Merge internal discovery candidates without publishing them.

    A successful discovery response replaces that source's old candidates.
    Not-due, unchanged, and failed sources retain their previous queue entries.
    """
    existing_candidates = existing.get("candidates") if isinstance(existing, dict) else []
    if not isinstance(existing_candidates, list):
        existing_candidates = []
    configured_source_ids = {result.source_id for result in batch.results}
    replace_source_ids = {result.source_id for result in batch.results if result.replace_candidates}
    retained = [
        candidate
        for candidate in existing_candidates
        if isinstance(candidate, dict)
        and candidate.get("sourceId") in configured_source_ids
        and candidate.get("sourceId") not in replace_source_ids
        and candidate.get("collectionMode") == "DISCOVERY_ONLY"
        and candidate.get("publishable") is False
    ]

    merged: list[dict[str, Any]] = []
    seen: set[str] = set()
    for candidate in [*batch.candidates, *retained]:
        if not isinstance(candidate, dict):
            continue
        if candidate.get("collectionMode") != "DISCOVERY_ONLY" or candidate.get("publishable") is not False:
            continue
        key = str(candidate.get("candidateDigest") or candidate.get("id") or "")
        if not key or key in seen:
            continue
        seen.add(key)
        merged.append(candidate)

    return {
        "version": 1,
        "updatedAt": batch.state.get("updatedAt"),
        "publicationAllowed": False,
        "notice": (
            "Internal discovery queue only. Provider permission and fresh date/venue verification are "
            "required before any candidate can enter popup-trends.json."
        ),
        "candidates": merged,
    }


def build_status_document(config: dict, batch: CollectionBatch) -> dict:
    configured_sources = [source for source in config.get("sources", []) if isinstance(source, dict)]
    source_states = batch.state.get("sources") if isinstance(batch.state.get("sources"), dict) else {}
    sources: list[dict[str, Any]] = []
    healthy_statuses = {
        "success",
        "not_modified",
        "unchanged",
        "discovery_ready",
        "discovery_empty",
        "manual_fallback",
    }

    for source in configured_sources:
        source_id = str(source.get("id") or "unknown-source")
        state = source_states.get(source_id) if isinstance(source_states.get(source_id), dict) else {}
        schedule = source_schedule(source)
        sources.append(
            {
                "sourceId": source_id,
                "sourceType": source.get("sourceType"),
                "sourceUrl": state.get("sourceUrl") or source.get("officialSourceUrl"),
                "status": state.get("lastStatus") or "never_run",
                "lastAttemptAt": state.get("lastAttemptAt"),
                "lastSuccessAt": state.get("lastSuccessAt"),
                "nextRunAt": state.get("nextRunAt"),
                "intervalSeconds": schedule["intervalSeconds"],
                "jitterSeconds": schedule["jitterSeconds"],
                "lastHttpStatus": state.get("lastHttpStatus"),
                "recordCount": state.get("recordCount", 0),
                "candidateCount": state.get("candidateCount", 0),
                "failureCount": state.get("failureCount", 0),
                "disabled": state.get("disabled", False),
                "disabledReason": state.get("disabledReason"),
                "lastError": state.get("lastError"),
            }
        )

    status_counts: dict[str, int] = {}
    for source in sources:
        source_status = str(source["status"])
        status_counts[source_status] = status_counts.get(source_status, 0) + 1

    return {
        "version": 1,
        "updatedAt": batch.state.get("updatedAt"),
        "summary": {
            "configuredSources": len(sources),
            "healthySources": sum(1 for source in sources if source["status"] in healthy_statuses),
            "disabledSources": sum(
                1
                for source in sources
                if source["disabled"] or str(source["status"]).startswith("disabled")
            ),
            "statusCounts": status_counts,
            "reviewCandidateCount": sum(
                int(source.get("candidateCount") or 0)
                for source in sources
                if source.get("sourceType") == "discovery"
            ),
        },
        "sources": sources,
    }


def has_unavailable_required_source(config: dict, batch: CollectionBatch) -> bool:
    unavailable_statuses = {
        "backoff",
        "disabled_auth",
        "disabled_missing_env",
        "disabled_previous",
        "disabled_sample",
        "error",
        "invalid_config",
    }
    required_source_ids = {
        str(source.get("id"))
        for source in config.get("sources", [])
        if isinstance(source, dict)
        and source.get("enabled") is not False
        and source.get("id")
        and isinstance(source.get("requiredEnv"), list)
        and any(str(name).strip() for name in source["requiredEnv"])
    }
    return any(
        result.source_id in required_source_ids and result.status in unavailable_statuses
        for result in batch.results
    )


def collect_once(
    config_path: Path,
    output_path: Path,
    timeout: int,
    min_records: int,
    state_path: Path | None = None,
    status_path: Path | None = None,
    review_path: Path | None = None,
    *,
    force: bool = False,
) -> int:
    resolved_state_path = state_path or output_path.parent / DEFAULT_STATE_PATH.name
    resolved_status_path = status_path or output_path.parent / DEFAULT_STATUS_PATH.name
    resolved_review_path = review_path or output_path.parent / DEFAULT_REVIEW_PATH.name
    config = read_config(config_path)
    state = read_json_file(resolved_state_path, {"version": 1, "sources": {}})
    existing = read_json_file(output_path, [])
    if not isinstance(existing, list):
        existing = []
    existing_review = read_json_file(resolved_review_path, {"version": 1, "candidates": []})

    batch = collect_due_sources(config, timeout, state, force=force)
    collection_as_of = parse_timestamp(batch.state.get("updatedAt"))
    records = [
        record
        for record in merge_records(existing, batch, config)
        if is_verifiable_record(record, as_of=collection_as_of)
    ]
    review_queue = merge_candidate_queue(existing_review, batch)
    status_document = build_status_document(config, batch)

    verified_count = len(records)
    required_source_unavailable = (
        verified_count == 0 and has_unavailable_required_source(config, batch)
    )
    if verified_count < min_records or required_source_unavailable:
        sanitized = records != existing
        if sanitized:
            write_records_atomic(output_path, records)
        write_json_atomic(resolved_review_path, review_queue)
        write_json_atomic(resolved_status_path, status_document)
        # Commit cache validators only after the output they describe is safely
        # published. If output replacement fails, the old state forces a safe
        # refetch on the next collector run.
        write_json_atomic(resolved_state_path, batch.state)
        action = "sanitized existing file" if sanitized else "preserving verified existing file"
        failure_reason = (
            "required source unavailable with no fresh verified records"
            if required_source_unavailable
            else f"expected at least {min_records} verified network records; got {verified_count}"
        )
        print(
            f"{failure_reason}; {action}",
            file=sys.stderr,
            flush=True,
        )
        return 2
    if (
        records != existing
        or any(result.replace_records or result.refresh_retained_records for result in batch.results)
        or not output_path.is_file()
    ):
        write_records_atomic(output_path, records)
    write_json_atomic(resolved_review_path, review_queue)
    write_json_atomic(resolved_status_path, status_document)
    write_json_atomic(resolved_state_path, batch.state)
    print(
        f"kept {len(records)} popup trend records ({verified_count} verified network); "
        f"{len(review_queue['candidates'])} internal review candidates; source status: {resolved_status_path}",
        flush=True,
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Refresh NugulMap Season 3 popup/event trend records.")
    parser.add_argument("--config", default=os.environ.get("POPUP_TRENDS_SOURCE_CONFIG", str(DEFAULT_CONFIG_PATH)))
    parser.add_argument("--output", default=os.environ.get("POPUP_TRENDS_FILE", str(DEFAULT_OUTPUT_PATH)))
    parser.add_argument("--state", default=os.environ.get("POPUP_TRENDS_STATE_FILE", str(DEFAULT_STATE_PATH)))
    parser.add_argument("--status", default=os.environ.get("POPUP_TRENDS_STATUS_FILE", str(DEFAULT_STATUS_PATH)))
    parser.add_argument(
        "--review-queue",
        default=os.environ.get("POPUP_TRENDS_REVIEW_FILE", str(DEFAULT_REVIEW_PATH)),
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=positive_int(os.environ.get("POPUP_TRENDS_TIMEOUT_SECONDS", ""), 10),
    )
    parser.add_argument(
        "--min-records",
        type=int,
        default=nonnegative_int(os.environ.get("POPUP_TRENDS_MIN_RECORDS", ""), 0),
    )
    parser.add_argument(
        "--interval-seconds",
        type=int,
        default=positive_int(os.environ.get("POPUP_TRENDS_REFRESH_SECONDS", ""), 300),
    )
    parser.add_argument(
        "--once",
        action="store_true",
        default=os.environ.get("POPUP_TRENDS_RUN_ONCE", "").lower() == "true",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        default=os.environ.get("POPUP_TRENDS_FORCE_REFRESH", "").lower() == "true",
    )
    args = parser.parse_args()

    config_path = Path(args.config)
    output_path = Path(args.output)
    state_path = Path(args.state)
    status_path = Path(args.status)
    review_path = Path(args.review_queue)

    if args.once:
        return collect_once(
            config_path,
            output_path,
            args.timeout,
            args.min_records,
            state_path,
            status_path,
            review_path,
            force=args.force,
        )

    while True:
        exit_code = collect_once(
            config_path,
            output_path,
            args.timeout,
            args.min_records,
            state_path,
            status_path,
            review_path,
            force=args.force,
        )
        if exit_code != 0:
            print("popup trend refresh completed with warnings; keeping collector alive", file=sys.stderr, flush=True)
        time.sleep(args.interval_seconds)


if __name__ == "__main__":
    raise SystemExit(main())
