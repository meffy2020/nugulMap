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


def read_optional(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        return ""
    return target.read_text(encoding="utf-8", errors="ignore")


def match_int(pattern: str, text: str) -> int | None:
    found = re.search(pattern, text)
    return int(found.group(1)) if found else None


def build_setting_values(name: str, text: str) -> list[str]:
    return [value.strip().strip('"') for value in re.findall(rf"{re.escape(name)}\s*=\s*([^;]+);", text)]


def has_nonempty_build_setting(name: str, text: str) -> bool:
    values = build_setting_values(name, text)
    return bool(values) and all(value not in {"", "$(inherited)"} for value in values)


def add(checks: list[Check], status: str, area: str, item: str, detail: str, action: str = "") -> None:
    checks.append(Check(status=status, area=area, item=item, detail=detail, action=action))


def detect_native_account_delete(paths: Iterable[str]) -> bool:
    literal_needles = ("delete account", "account deletion", "회원탈퇴", "회원 탈퇴", "계정 삭제", "탈퇴")
    call_patterns = (r"\bdeleteAccount\s*\(", r"\bdeleteUser\s*\(")
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
                lowered = text.lower()
                if any(needle in lowered for needle in literal_needles[:2]) or any(needle in text for needle in literal_needles[2:]):
                    return True
                if any(re.search(pattern, text) for pattern in call_patterns):
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

    if has_nonempty_build_setting("DEVELOPMENT_TEAM", ios_project):
        add(checks, "PASS", "ios", "development-team", "DEVELOPMENT_TEAM is set")
    else:
        add(checks, "FAIL", "ios", "development-team", "DEVELOPMENT_TEAM is empty for one or more target configurations", "Configure Apple Developer Team ID in local/Xcode signing settings; do not commit secrets")

    code_sign_values = set(build_setting_values("CODE_SIGN_STYLE", ios_project))
    if code_sign_values:
        add(checks, "PASS", "ios", "code-sign-style", ", ".join(sorted(code_sign_values)))
    else:
        add(checks, "FAIL", "ios", "code-sign-style", "missing", "Configure signing style before archive")

    bundle = re.search(r"PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^;]+);", ios_project)
    add(checks, "PASS" if bundle else "FAIL", "ios", "bundle-id", bundle.group(1).strip() if bundle else "missing", "Register bundle id in App Store Connect" if bundle else "Set bundle id")

    marketing_version = build_setting_values("MARKETING_VERSION", ios_project)
    current_project_version = build_setting_values("CURRENT_PROJECT_VERSION", ios_project)
    if marketing_version and current_project_version:
        add(checks, "PASS", "ios", "versioning", f"MARKETING_VERSION={marketing_version[-1]}, CURRENT_PROJECT_VERSION={current_project_version[-1]}", "Increment CURRENT_PROJECT_VERSION for every TestFlight upload")
    else:
        add(checks, "FAIL", "ios", "versioning", "missing MARKETING_VERSION or CURRENT_PROJECT_VERSION", "Set App Store version/build numbers")

    if has_file("ios-native/NeogulMapNative.xcodeproj/xcshareddata/xcschemes/NeogulMapNative.xcscheme"):
        add(checks, "PASS", "ios", "shared-archive-scheme", "NeogulMapNative shared scheme exists")
    else:
        add(checks, "FAIL", "ios", "shared-archive-scheme", "shared scheme missing", "Share the app scheme before CI/archive verification")

    if has_nonempty_build_setting("DEVELOPMENT_TEAM", ios_project):
        add(checks, "MANUAL", "ios", "archive-testflight", "Signing team exists, but App Store Connect archive/upload is account-gated", "Run generic iOS archive, validate, and upload/TestFlight with the Developer account")
    else:
        add(checks, "FAIL", "ios", "archive-testflight", "Archive/TestFlight is blocked by empty DEVELOPMENT_TEAM", "Set signing team/profiles, then run archive validation")

    if (
        "nugulmap://oauth/callback" in ios_app_config
        and "<string>nugulmap</string>" in ios_plist
        and "callbackURLScheme: AppConfig.oauthCallbackScheme" in ios_api
    ):
        add(checks, "PASS", "ios", "oauth-url-scheme", "nugulmap://oauth/callback configured in plist, AppConfig, and ASWebAuthenticationSession")
    else:
        add(checks, "FAIL", "ios", "oauth-url-scheme", "callback URL scheme missing or not wired to ASWebAuthenticationSession", "Register URL scheme and AppConfig callback")

    if has_file("ios-native/scripts/smoke-oauth-deeplink.sh"):
        add(checks, "MANUAL", "ios", "oauth-device-smoke", "Local deeplink smoke script exists; token exchange still needs real provider/device verification", "Run simulator script and real iPhone OAuth callback/token persistence smoke")
    else:
        add(checks, "FAIL", "ios", "oauth-device-smoke", "No repeatable OAuth deeplink smoke script", "Add simulator/device OAuth smoke instructions")

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

    user_controller = read("backend/api-server/src/main/java/com/neogulmap/neogul_map/controller/UserController.java")
    backend_delete = "@DeleteMapping" in user_controller and "deleteUser" in user_controller
    ios_account_delete = detect_native_account_delete(("ios-native/NeogulMapNative",))
    android_account_delete = detect_native_account_delete(("android-native/app/src",))
    add(checks, "PASS" if backend_delete else "FAIL", "shared", "backend-account-delete", "UserController delete endpoint detected" if backend_delete else "No backend user delete endpoint detected", "Implement backend account deletion" if not backend_delete else "")
    if ios_account_delete:
        add(checks, "PASS", "ios", "account-deletion-ux", "Discoverable iOS account deletion path detected")
    else:
        add(checks, "FAIL", "ios", "account-deletion-ux", "No discoverable iOS account deletion path detected", "Add in-app account deletion initiation under profile/settings before App Store submission")
    if android_account_delete:
        add(checks, "PASS", "android", "account-deletion-ux", "Discoverable Android account deletion path detected")
    else:
        add(checks, "FAIL", "android", "account-deletion-ux", "No discoverable Android account deletion path detected", "Add in-app account deletion initiation before Play submission")

    privacy_inventory = read_optional("docs/store-privacy-readiness.md")
    required_privacy_sections = (
        "Google Play Data Safety checklist",
        "Apple App Privacy checklist",
        "Store metadata checklist",
        "Account deletion acceptance checklist",
        "Review-risk gates before public submission",
    )
    required_data_terms = (
        "OAuth email",
        "OAuth provider ID",
        "Nickname",
        "Profile image",
        "Device current location",
        "Zone photo/image",
        "Review content",
        "Access/refresh tokens",
        "Diagnostics/logs/IP",
    )
    missing_sections = [section for section in required_privacy_sections if section not in privacy_inventory]
    missing_terms = [term for term in required_data_terms if term not in privacy_inventory]
    if privacy_inventory and not missing_sections and not missing_terms:
        add(checks, "PASS", "shared", "privacy-data-inventory", "Store privacy inventory covers Play, Apple, metadata, deletion, review risks, and core data types")
    else:
        detail = "missing " + ", ".join(missing_sections + missing_terms) if privacy_inventory else "docs/store-privacy-readiness.md missing"
        add(checks, "FAIL", "shared", "privacy-data-inventory", detail, "Create/update store privacy data inventory before submission")

    public_url_pattern = re.compile(r"https://(?!support\.google\.com|developer\.apple\.com)[^\s)]+", re.I)
    public_urls = public_url_pattern.findall(privacy_inventory)
    if public_urls:
        add(checks, "MANUAL", "shared", "privacy-policy-url", f"candidate public URL(s): {', '.join(sorted(set(public_urls)))}", "Confirm URL is public, non-placeholder, and entered in both store consoles")
    else:
        add(checks, "FAIL", "shared", "privacy-policy-url", "No public NugulMap privacy/account-deletion URL recorded", "Publish privacy policy and account-deletion URL before public submission")

    repo_privacy_text = "\n".join(
        read_optional(path)
        for path in ["mobile/README.md", "TODO.md", "docs/mobile-public-launch-readiness.md", "docs/store-privacy-readiness.md"]
    )
    if "Play Data Safety" in repo_privacy_text and "Apple App Privacy" in repo_privacy_text:
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
