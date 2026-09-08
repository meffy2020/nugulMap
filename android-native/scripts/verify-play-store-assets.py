#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import struct
import sys
from dataclasses import dataclass
from pathlib import Path


ANDROID_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ANDROID_ROOT / "store-assets/google-play/manifest.json"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


@dataclass(frozen=True)
class PngInfo:
    width: int
    height: int
    bit_depth: int
    color_type: int
    has_transparency_chunk: bool

    @property
    def has_alpha(self) -> bool:
        return self.color_type in {4, 6} or self.has_transparency_chunk


def read_png_info(path: Path) -> PngInfo:
    with path.open("rb") as stream:
        if stream.read(8) != PNG_SIGNATURE:
            raise ValueError("not a PNG file")

        width = height = bit_depth = color_type = None
        has_transparency_chunk = False
        while True:
            length_bytes = stream.read(4)
            if len(length_bytes) != 4:
                raise ValueError("truncated PNG")
            length = struct.unpack(">I", length_bytes)[0]
            chunk_type = stream.read(4)
            chunk_data = stream.read(length)
            crc = stream.read(4)
            if len(chunk_type) != 4 or len(chunk_data) != length or len(crc) != 4:
                raise ValueError("truncated PNG chunk")
            if chunk_type == b"IHDR":
                width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", chunk_data)
            elif chunk_type == b"tRNS":
                has_transparency_chunk = True
            elif chunk_type == b"IEND":
                break

    if None in {width, height, bit_depth, color_type}:
        raise ValueError("missing PNG IHDR")
    return PngInfo(
        width=int(width),
        height=int(height),
        bit_depth=int(bit_depth),
        color_type=int(color_type),
        has_transparency_chunk=has_transparency_chunk,
    )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_entry(entry: dict[str, object], failures: list[str]) -> None:
    failure_count_before = len(failures)
    relative_path = str(entry["path"])
    path = ANDROID_ROOT / relative_path
    if not path.is_file():
        failures.append(f"missing asset: {relative_path}")
        return

    try:
        info = read_png_info(path)
    except ValueError as error:
        failures.append(f"{relative_path}: {error}")
        return

    kind = str(entry["kind"])
    expected_width = int(entry["width"])
    expected_height = int(entry["height"])
    if (info.width, info.height) != (expected_width, expected_height):
        failures.append(
            f"{relative_path}: expected {expected_width}x{expected_height}, "
            f"got {info.width}x{info.height}"
        )
    if info.bit_depth != 8 or info.color_type != 2:
        failures.append(
            f"{relative_path}: expected 24-bit RGB PNG (bit depth 8, color type 2), "
            f"got bit depth {info.bit_depth}, color type {info.color_type}"
        )
    if info.has_alpha:
        failures.append(f"{relative_path}: alpha/transparency is not allowed")

    expected_hash = str(entry["sha256"])
    actual_hash = sha256(path)
    if actual_hash != expected_hash:
        failures.append(f"{relative_path}: SHA-256 does not match manifest")

    source_relative_path = entry.get("source")
    expected_source_hash = entry.get("sourceSha256")
    if source_relative_path and expected_source_hash:
        source_path = (ANDROID_ROOT / str(source_relative_path)).resolve()
        if not source_path.is_file():
            failures.append(f"{relative_path}: missing declared source {source_relative_path}")
        elif sha256(source_path) != str(expected_source_hash):
            failures.append(f"{relative_path}: source SHA-256 does not match manifest")

    required_dimensions = {
        "icon": (512, 512),
        "featureGraphic": (1024, 500),
    }
    required_size = required_dimensions.get(kind)
    if required_size is not None and (info.width, info.height) != required_size:
        failures.append(
            f"{relative_path}: {kind} must be {required_size[0]}x{required_size[1]}"
        )

    if kind == "screenshot":
        shortest = min(info.width, info.height)
        longest = max(info.width, info.height)
        if shortest < 320 or longest > 3840:
            failures.append(f"{relative_path}: dimensions must remain within 320..3840 px")
        if longest > shortest * 2:
            failures.append(f"{relative_path}: longest side exceeds twice the shortest side")

    if len(failures) == failure_count_before:
        print(
            f"PASS {relative_path}: {info.width}x{info.height}, "
            f"RGB24, alpha=no, sha256={actual_hash[:12]}..."
        )


def main() -> int:
    if not MANIFEST_PATH.is_file():
        print(f"FAIL missing manifest: {MANIFEST_PATH}", file=sys.stderr)
        return 1

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    assets = manifest["assets"]
    screenshots = assets["screenshots"]
    failures: list[str] = []

    expected = {"01-smoking-zones.png", "02-smoking-detail.png", "03-menu.png", "04-settings-and-policy.png"}
    candidate_dir = ANDROID_ROOT / "store-assets/google-play/screenshots-smoking"
    declared = {Path(str(entry["path"])).name for entry in screenshots}
    if manifest.get("scope") != "smoking-zones-only" or declared != expected:
        failures.append("only the four reviewed smoking-scope captures may be candidates")
    if candidate_dir.exists() and {path.name for path in candidate_dir.glob("*.png")} != expected:
        failures.append("candidate folder has unexpected screenshots")
    if any("/screenshots-smoking/" not in str(entry["path"]) for entry in screenshots):
        failures.append("historical screenshots are excluded from upload candidates")

    if len(screenshots) < 4:
        failures.append("at least four Play screenshots are required")
    release_gate = manifest.get("releaseGate", {})
    if release_gate.get("required") is not True or "signed release candidate" not in str(
        release_gate.get("requirement", "")
    ).lower():
        failures.append("manifest must retain the signed release candidate recapture gate")

    verify_entry(assets["icon"], failures)
    verify_entry(assets["featureGraphic"], failures)
    for screenshot in screenshots:
        verify_entry(screenshot, failures)

    if failures:
        for failure in failures:
            print(f"FAIL {failure}", file=sys.stderr)
        print(f"Result: FAIL ({len(failures)} issue(s))", file=sys.stderr)
        return 1

    print(f"Result: PASS ({len(screenshots)} Play screenshots plus icon and feature graphic)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
