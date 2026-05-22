#!/usr/bin/env python3
"""Static NugulMap native public-launch readiness audit.

This script intentionally does not require store credentials. It reports what can
be proven from the repository and flags account/policy/device work as blockers
or manual gates for public launch readiness.
"""
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Check:
    status: str
    area: str
    item: str
    detail: str
    action: str = ""


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def has_file(path: str) -> bool:
    return (ROOT / path).exists()


def match_int(pattern: str, text: str) -> int | None:
    found = re.search(pattern, text)
    return int(found.group(1)) if found else None


def add(checks: list[Check], status: str, area: str, item: str, detail: str, action: str = "") -> None:
    checks.append(Check(status=status, area=area, item=item, detail=detail, action=action))


def detect_native_account_delete(paths: Iterable[str]) -> bool:
    needles = ("delete account", "account deletion", "회원탈퇴", "계정 삭제", "탈퇴", "deleteUser", "deleteAccount")
    for rel in paths:
        base = ROOT / rel
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path.is_file() and path.suffix.lower() in {".kt", ".swift", ".xml", ".plist"}:
                try:
                    text = path.read_text(encoding="utf-8", errors="ignore")
                except OSError:
                    continue
                if any(needle in text for needle in needles):
                    return True
    return False


def main() -> int:
    checks: list[Check] = []

    android_build = read("android-native/app/build.gradle.kts")
    android_manifest = read("android-native/app/src/main/AndroidManifest.xml")
    android_libs = read("android-native/gradle/libs.versions.toml")
    ios_project = read("ios-native/NeogulMapNative.xcodeproj/project.pbxproj")
    ios_plist = read("ios-native/NeogulMapNative/Info.plist")
    ios_app_config = read("ios-native/NeogulMapNative/AppConfig.swift")
    ios_api = read("ios-native/NeogulMapNative/Services/NugulAPIClient.swift")
    ios_login = read("ios-native/NeogulMapNative/Views/ZoneMapView.swift")

    target_sdk = match_int(r"targetSdk\s*=\s*(\d+)", android_build)
    if target_sdk is not None and target_sdk >= 35:
        add(checks, "PASS", "android", "target-sdk", f"targetSdk={target_sdk} satisfies current Play target API floor")
    else:
        add(checks, "FAIL", "android", "target-sdk", f"targetSdk={target_sdk}", "Set targetSdk >= 35 before Play submission")

    app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', android_build)
    add(checks, "PASS" if app_id else "FAIL", "android", "application-id", app_id.group(1) if app_id else "missing", "Confirm Play Console package id" if app_id else "Set applicationId")

    version_code = match_int(r"versionCode\s*=\s*(\d+)", android_build)
    add(checks, "PASS" if version_code and version_code > 0 else "FAIL", "android", "version-code", f"versionCode={version_code}", "Increment before each upload")

    if "nugulmap.com" in android_build and "KAKAO_NATIVE_APP_KEY" in android_build:
        add(checks, "PASS", "android", "production-config-hooks", "API base and Kakao native key are build-configurable")
    else:
        add(checks, "FAIL", "android", "production-config-hooks", "missing API/key config", "Add production API and key injection")

    local_props = ROOT / "android-native/local.properties"
    if local_props.exists() and re.search(r"^KAKAO_NATIVE_APP_KEY=\S+", local_props.read_text(encoding="utf-8", errors="ignore"), re.M):
        add(checks, "PASS", "android", "kakao-native-key-local", "KAKAO_NATIVE_APP_KEY present in local.properties")
    else:
        add(checks, "FAIL", "android", "kakao-native-key-local", "No local production Kakao native key detected", "Set local.properties and verify Kakao console package/key hash on device")

    if all(token in android_manifest for token in ('android:scheme="nugulmap"', 'android:host="oauth"', 'android:path="/callback"')):
        add(checks, "PASS", "android", "oauth-deeplink", "nugulmap://oauth/callback registered")
    else:
        add(checks, "FAIL", "android", "oauth-deeplink", "callback intent-filter missing", "Register OAuth callback deeplink")

    if "kakaoMap" in android_libs or "kakao-map" in android_libs:
        add(checks, "MANUAL", "android", "16kb-page-size", "Native Kakao Maps dependency present; repo static check cannot prove 16KB page-size", "Validate signed AAB with bundletool/Play pre-launch report")

    if "isMinifyEnabled = false" in android_build:
        add(checks, "MANUAL", "android", "release-minify-policy", "Release minification is disabled", "Accept explicitly for v1 or enable and test ProGuard/R8")

    if "DEVELOPMENT_TEAM = \"\"" in ios_project:
        add(checks, "FAIL", "ios", "development-team", "DEVELOPMENT_TEAM is empty", "Configure Apple Developer Team ID outside secrets")
    else:
        add(checks, "PASS", "ios", "development-team", "DEVELOPMENT_TEAM is set")

    bundle = re.search(r"PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^;]+);", ios_project)
    add(checks, "PASS" if bundle else "FAIL", "ios", "bundle-id", bundle.group(1).strip() if bundle else "missing", "Register bundle id in App Store Connect" if bundle else "Set bundle id")

    if "nugulmap://oauth/callback" in ios_app_config and "<string>nugulmap</string>" in ios_plist:
        add(checks, "PASS", "ios", "oauth-url-scheme", "nugulmap://oauth/callback configured")
    else:
        add(checks, "FAIL", "ios", "oauth-url-scheme", "callback URL scheme missing", "Register URL scheme and AppConfig callback")

    if "NSLocationWhenInUseUsageDescription" in ios_plist:
        add(checks, "PASS", "ios", "location-purpose-string", "Location usage description exists")
    else:
        add(checks, "FAIL", "ios", "location-purpose-string", "Missing location purpose string", "Add NSLocationWhenInUseUsageDescription")

    uses_social = all(provider in ios_api for provider in ("case kakao", "case naver", "case google")) and all(label in ios_login for label in ("카카오", "네이버", "구글"))
    uses_apple = "case apple" in ios_api or "Sign in with Apple" in ios_login or "Apple로" in ios_login
    if uses_social and not uses_apple:
        add(checks, "FAIL", "ios", "apple-login-review-risk", "Kakao/Naver/Google login are present but Apple-equivalent login was not detected", "Resolve App Store Review Guideline 4.8 or document a valid exception")
    elif uses_social:
        add(checks, "PASS", "ios", "apple-login-review-risk", "Social login and Apple-equivalent login detected")
    else:
        add(checks, "MANUAL", "ios", "apple-login-review-risk", "No social login pattern detected by static scan")

    backend_delete = "@DeleteMapping" in read("backend/api-server/src/main/java/com/neogulmap/neogul_map/controller/UserController.java")
    native_delete = detect_native_account_delete(("android-native/app/src", "ios-native/NeogulMapNative"))
    if backend_delete and native_delete:
        add(checks, "PASS", "shared", "account-deletion", "Backend and native account deletion paths detected")
    elif backend_delete:
        add(checks, "FAIL", "shared", "account-deletion", "Backend delete endpoint exists, but native discoverable account deletion UX was not detected", "Add or document in-app account deletion path before public launch")
    else:
        add(checks, "FAIL", "shared", "account-deletion", "No backend/native account deletion evidence detected", "Implement compliant account deletion")

    privacy_candidates = ["privacy", "개인정보", "Privacy Policy", "privacy policy"]
    repo_privacy_text = "\n".join(
        path.read_text(encoding="utf-8", errors="ignore")
        for path in [ROOT / "mobile/README.md", ROOT / "TODO.md", ROOT / "docs/mobile-public-launch-readiness.md"]
        if path.exists()
    )
    if any(token in repo_privacy_text for token in privacy_candidates):
        add(checks, "MANUAL", "shared", "privacy-policy-and-store-forms", "Privacy/store checklist exists, but final public URL/forms are account-gated", "Complete Play Data Safety and Apple App Privacy from data inventory")
    else:
        add(checks, "FAIL", "shared", "privacy-policy-and-store-forms", "No privacy/store checklist evidence", "Create privacy policy and store form drafts")

    statuses = {"PASS": 0, "MANUAL": 0, "FAIL": 0}
    for check in checks:
        statuses[check.status] = statuses.get(check.status, 0) + 1
        suffix = f" | action: {check.action}" if check.action else ""
        print(f"[{check.status}] {check.area}:{check.item} - {check.detail}{suffix}")

    print("\nSummary:", json.dumps(statuses, ensure_ascii=False, sort_keys=True))
    if statuses.get("FAIL", 0):
        print("Result: NOT PUBLIC-LAUNCH READY (expected until store/account/device blockers are resolved)")
    else:
        print("Result: STATIC GATES CLEAR; continue with manual store/device gates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
