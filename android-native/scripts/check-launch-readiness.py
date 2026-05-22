#!/usr/bin/env python3
"""Android-native public launch readiness audit for NugulMap.

The script is intentionally safe for local/team worktrees: it never reads or
prints secret values, never submits to Play, and treats account/device gates as
actionable FAIL/MANUAL checks rather than pretending they are solved.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Check:
    status: str
    item: str
    detail: str
    action: str = ""


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="ignore")


def local_properties() -> dict[str, str]:
    path = ROOT / "local.properties"
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def prop_or_env(props: dict[str, str], name: str) -> str:
    return (props.get(name) or os.environ.get(name) or "").strip()


def match_int(pattern: str, text: str) -> int | None:
    found = re.search(pattern, text)
    return int(found.group(1)) if found else None


def add(checks: list[Check], status: str, item: str, detail: str, action: str = "") -> None:
    checks.append(Check(status=status, item=item, detail=detail, action=action))


def first_existing(paths: list[Path]) -> Path | None:
    for path in paths:
        if path.exists():
            return path
    return None


def bundletool_command() -> list[str] | None:
    binary = shutil.which("bundletool")
    if binary:
        return [binary]
    jar = os.environ.get("BUNDLETOOL_JAR")
    if jar and Path(jar).exists():
        return ["java", "-jar", jar]
    return None


def check_bundle_alignment(aab_path: Path) -> tuple[str, str]:
    command = bundletool_command()
    if command is None:
        return (
            "MANUAL",
            "bundletool unavailable; cannot inspect AAB PAGE_ALIGNMENT_16K flag",
        )
    result = subprocess.run(
        [*command, "dump", "config", f"--bundle={aab_path}"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        return ("MANUAL", f"bundletool config dump failed with exit {result.returncode}")
    output = result.stdout
    if "PAGE_ALIGNMENT_16K" in output:
        return ("PASS", "AAB requests PAGE_ALIGNMENT_16K")
    return ("FAIL", "AAB config did not report PAGE_ALIGNMENT_16K")


def main() -> int:
    checks: list[Check] = []
    props = local_properties()

    build = read("app/build.gradle.kts")
    manifest = read("app/src/main/AndroidManifest.xml")
    libs = read("gradle/libs.versions.toml")

    compile_sdk = match_int(r"compileSdk\s*=\s*(\d+)", build)
    target_sdk = match_int(r"targetSdk\s*=\s*(\d+)", build)
    if target_sdk is not None and target_sdk >= 35:
        add(checks, "PASS", "target-sdk", f"targetSdk={target_sdk} satisfies Play's Android 15/API 35 floor")
    else:
        add(checks, "FAIL", "target-sdk", f"targetSdk={target_sdk}", "Set targetSdk >= 35 before Play submission")
    if compile_sdk is not None and target_sdk is not None and compile_sdk >= target_sdk:
        add(checks, "PASS", "compile-sdk", f"compileSdk={compile_sdk} covers targetSdk={target_sdk}")
    else:
        add(checks, "FAIL", "compile-sdk", f"compileSdk={compile_sdk}, targetSdk={target_sdk}", "Use compileSdk >= targetSdk")

    app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', build)
    add(
        checks,
        "PASS" if app_id else "FAIL",
        "application-id",
        app_id.group(1) if app_id else "missing",
        "Register/confirm this package name in Play Console" if app_id else "Set applicationId",
    )

    version_code = match_int(r"versionCode\s*=\s*(\d+)", build)
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', build)
    if version_code and version_code > 0 and version_name:
        add(checks, "PASS", "versioning", f"versionCode={version_code}, versionName={version_name.group(1)}")
    else:
        add(checks, "FAIL", "versioning", f"versionCode={version_code}, versionName={version_name.group(1) if version_name else None}", "Set upload-ready version values")

    signing_names = [
        "NUGUL_RELEASE_STORE_FILE",
        "NUGUL_RELEASE_STORE_PASSWORD",
        "NUGUL_RELEASE_KEY_ALIAS",
        "NUGUL_RELEASE_KEY_PASSWORD",
    ]
    signing_values = {name: prop_or_env(props, name) for name in signing_names}
    if all(signing_values.values()):
        store_file = Path(signing_values["NUGUL_RELEASE_STORE_FILE"])
        store_path = store_file if store_file.is_absolute() else ROOT / store_file
        add(
            checks,
            "PASS" if store_path.exists() else "FAIL",
            "release-signing-config",
            f"release signing properties present; keystore file {'exists' if store_path.exists() else 'missing'}",
            "Create/provide the upload keystore file outside git" if not store_path.exists() else "",
        )
    else:
        add(
            checks,
            "FAIL",
            "release-signing-config",
            "upload-key signing properties are incomplete or absent",
            "Set NUGUL_RELEASE_* in local.properties or environment before signed AAB upload",
        )

    kakao_key = prop_or_env(props, "KAKAO_NATIVE_APP_KEY")
    add(
        checks,
        "PASS" if kakao_key else "FAIL",
        "kakao-native-key",
        "KAKAO_NATIVE_APP_KEY configured locally/env" if kakao_key else "KAKAO_NATIVE_APP_KEY absent",
        "Set the Kakao Android native app key and verify package/key hash in Kakao Developers" if not kakao_key else "",
    )
    add(
        checks,
        "MANUAL",
        "kakao-key-hash",
        "Kakao package/key-hash registration cannot be proven from repo secrets-free state",
        "Run keytool/openssl against the Play upload key and register the Android key hash for com.nugulmap.nativeapp",
    )

    if all(token in manifest for token in ('android:scheme="nugulmap"', 'android:host="oauth"', 'android:path="/callback"')):
        add(checks, "PASS", "oauth-deeplink", "nugulmap://oauth/callback intent-filter registered")
    else:
        add(checks, "FAIL", "oauth-deeplink", "callback intent-filter missing", "Register nugulmap://oauth/callback")
    add(
        checks,
        "MANUAL",
        "oauth-device-smoke",
        "Static deeplink exists; real provider OAuth needs a device/emulator and provider credentials",
        "Run scripts/smoke-oauth-deeplink.sh, then login-button OAuth with real provider code",
    )

    agp = re.search(r'agp\s*=\s*"([^"]+)"', libs)
    if agp:
        major_minor_patch = tuple(int(part) for part in re.findall(r"\d+", agp.group(1))[:3])
        status = "PASS" if major_minor_patch >= (8, 5, 1) else "FAIL"
        add(checks, status, "agp-16kb-floor", f"AGP={agp.group(1)}", "Use AGP >= 8.5.1 for default 16KB-compatible packaging" if status == "FAIL" else "")
    else:
        add(checks, "FAIL", "agp-16kb-floor", "AGP version not detected")

    aab_path = first_existing([
        ROOT / "app/build/outputs/bundle/release/app-release.aab",
        ROOT / "app/build/outputs/bundle/release/app-release-unsigned.aab",
    ])
    if aab_path:
        add(checks, "PASS", "release-aab-artifact", str(aab_path.relative_to(ROOT)))
        with zipfile.ZipFile(aab_path) as archive:
            native_libs = [name for name in archive.namelist() if name.startswith("base/lib/") and name.endswith(".so")]
        if native_libs or "kakao-map" in libs:
            status, detail = check_bundle_alignment(aab_path)
            add(checks, status, "16kb-page-size", detail, "Install on a 16KB Android 15+ image or check Play pre-launch report" if status != "PASS" else "")
        else:
            add(checks, "PASS", "16kb-page-size", "No native .so files detected in release AAB")
    else:
        add(
            checks,
            "FAIL",
            "release-aab-artifact",
            "release AAB not found",
            "Run ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:bundleRelease",
        )
        if "kakao-map" in libs:
            add(
                checks,
                "MANUAL",
                "16kb-page-size",
                "Kakao Maps SDK may contribute native libraries; no AAB artifact to inspect",
                "Build the release AAB and inspect with bundletool dump config",
            )

    adb = shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")
    adb_path = Path(adb)
    if adb_path.exists():
        state = subprocess.run([str(adb_path), "get-state"], text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
        add(
            checks,
            "MANUAL" if state.returncode == 0 else "MANUAL",
            "device-smoke",
            "adb device connected" if state.returncode == 0 else "adb available but no connected device",
            "Run scripts/smoke-oauth-deeplink.sh and perform map/OAuth/login smoke on device",
        )
    else:
        add(checks, "MANUAL", "device-smoke", "adb not found", "Install Android platform-tools or set ADB")

    counts = {"PASS": 0, "MANUAL": 0, "FAIL": 0}
    for check in checks:
        counts[check.status] = counts.get(check.status, 0) + 1
        suffix = f" | action: {check.action}" if check.action else ""
        print(f"[{check.status}] android:{check.item} - {check.detail}{suffix}")

    print("\nSummary:", json.dumps(counts, ensure_ascii=False, sort_keys=True))
    if counts.get("FAIL", 0):
        print("Result: NOT ANDROID PUBLIC-LAUNCH READY")
    else:
        print("Result: STATIC ANDROID GATES CLEAR; continue with manual Play/device gates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
