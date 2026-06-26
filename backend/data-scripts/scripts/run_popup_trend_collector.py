"""Run the Season 2 popup/event trend collector once or on an interval."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

from collect_popup_trends import DEFAULT_CONFIG_PATH, DEFAULT_OUTPUT_PATH, collect, read_config


def positive_int(value: str, fallback: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return fallback
    return parsed if parsed > 0 else fallback


def write_records_atomic(output_path: Path, records: list[dict]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = output_path.with_suffix(f"{output_path.suffix}.tmp")
    temp_path.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    temp_path.replace(output_path)


def collect_once(config_path: Path, output_path: Path, timeout: int, min_records: int) -> int:
    records = collect(read_config(config_path), timeout)
    write_records_atomic(output_path, records)
    print(f"wrote {len(records)} popup trend records to {output_path}", flush=True)
    if len(records) < min_records:
        print(f"expected at least {min_records} popup trend records", file=sys.stderr, flush=True)
        return 2
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Refresh NugulMap Season 2 popup/event trend records.")
    parser.add_argument("--config", default=os.environ.get("POPUP_TRENDS_SOURCE_CONFIG", str(DEFAULT_CONFIG_PATH)))
    parser.add_argument("--output", default=os.environ.get("POPUP_TRENDS_FILE", str(DEFAULT_OUTPUT_PATH)))
    parser.add_argument("--timeout", type=int, default=positive_int(os.environ.get("POPUP_TRENDS_TIMEOUT_SECONDS", ""), 10))
    parser.add_argument("--min-records", type=int, default=positive_int(os.environ.get("POPUP_TRENDS_MIN_RECORDS", ""), 1))
    parser.add_argument(
        "--interval-seconds",
        type=int,
        default=positive_int(os.environ.get("POPUP_TRENDS_REFRESH_SECONDS", ""), 3600),
    )
    parser.add_argument("--once", action="store_true", default=os.environ.get("POPUP_TRENDS_RUN_ONCE", "").lower() == "true")
    args = parser.parse_args()

    config_path = Path(args.config)
    output_path = Path(args.output)

    if args.once:
        return collect_once(config_path, output_path, args.timeout, args.min_records)

    while True:
        exit_code = collect_once(config_path, output_path, args.timeout, args.min_records)
        if exit_code != 0:
            print("popup trend refresh completed with warnings; keeping collector alive", file=sys.stderr, flush=True)
        time.sleep(args.interval_seconds)


if __name__ == "__main__":
    raise SystemExit(main())
