#!/usr/bin/env python3
"""Static NugulMap native public-launch readiness audit.

This script intentionally does not require store credentials. It reports what can
be proven from the repository and flags account/policy/device work as blockers
or manual gates for public launch readiness.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile
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


def major_version(pattern: str, text: str) -> int | None:
    match = re.search(pattern, text, flags=re.IGNORECASE)
    return int(match.group(1)) if match else None


def build_setting_values(name: str, text: str) -> list[str]:
    return [value.strip().strip('"') for value in re.findall(rf"{re.escape(name)}\s*=\s*([^;]+);", text)]


def has_nonempty_build_setting(name: str, text: str) -> bool:
    values = build_setting_values(name, text)
    return bool(values) and all(
        value not in {"", "$(inherited)"} and not re.fullmatch(r"\$\([^)]+\)", value)
        for value in values
    )


def add(checks: list[Check], status: str, area: str, item: str, detail: str, action: str = "") -> None:
    checks.append(Check(status=status, area=area, item=item, detail=detail, action=action))


def checks_for_platform(checks: Iterable[Check], platform: str) -> list[Check]:
    if platform == "all":
        return list(checks)
    return [check for check in checks if check.area in {platform, "shared"}]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Audit NugulMap native store readiness for one platform or both."
    )
    parser.add_argument(
        "--platform",
        choices=("all", "android", "ios"),
        default="all",
        help="Select an independent submission lane; shared privacy/backend gates are always included.",
    )
    return parser.parse_args(argv)


def read_properties(path: Path) -> dict[str, str]:
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


def env_or_prop(props: dict[str, str], name: str) -> str:
    return (os.environ.get(name) or props.get(name) or "").strip()


def xcconfig_value(name: str, *texts: str) -> str:
    value = ""
    pattern = re.compile(rf"^\s*{re.escape(name)}\s*=\s*(.*?)\s*$")
    for text in texts:
        for raw_line in text.splitlines():
            line = raw_line.split("//", 1)[0]
            match = pattern.match(line)
            if match:
                value = match.group(1).strip().strip('"')
    return value


def resolved_apple_team_id(ios_project: str, release_config: str, secrets_config: str) -> str:
    team_pattern = re.compile(r"[A-Z0-9]{10}")
    candidates = [
        os.environ.get("NUGUL_DEVELOPMENT_TEAM", "").strip(),
        xcconfig_value("NUGUL_DEVELOPMENT_TEAM", release_config, secrets_config),
        *build_setting_values("DEVELOPMENT_TEAM", ios_project),
    ]
    return next((value for value in candidates if team_pattern.fullmatch(value)), "")


def bundletool_command() -> list[str] | None:
    binary = shutil.which("bundletool")
    if binary:
        return [binary]
    jar = os.environ.get("BUNDLETOOL_JAR")
    if jar and Path(jar).exists():
        return ["java", "-jar", jar]
    return None


def bundle_alignment_status(
    aab_path: Path,
    *,
    run_command=None,
    command_finder=None,
) -> tuple[str, str]:
    """Check AAB 16 KB packaging metadata and every bundled 64-bit ELF."""
    try:
        with zipfile.ZipFile(aab_path) as archive:
            libraries = sorted(
                name
                for name in archive.namelist()
                if re.fullmatch(r"base/lib/(?:arm64-v8a|x86_64)/.+\.so", name)
            )
            failures: list[str] = []
            for name in libraries:
                status, detail = elf64_load_alignment(archive.read(name))
                if status != "PASS":
                    failures.append(f"{name}: {detail}")
    except (OSError, zipfile.BadZipFile, KeyError) as exc:
        return ("FAIL", f"AAB inspection failed: {exc.__class__.__name__}")

    if failures:
        return ("FAIL", "; ".join(failures))

    finder = command_finder or bundletool_command
    command = finder()
    if command is None:
        return ("MANUAL", "release AAB exists, but bundletool is unavailable")
    runner = run_command or subprocess.run
    try:
        result = runner(
            [*command, "dump", "config", f"--bundle={aab_path}"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
    except OSError:
        return ("MANUAL", "bundletool could not be executed")
    if result.returncode != 0:
        return ("MANUAL", f"bundletool dump config failed with exit {result.returncode}")
    if "PAGE_ALIGNMENT_16K" not in (result.stdout or ""):
        return ("FAIL", "release AAB did not report PAGE_ALIGNMENT_16K")
    return (
        "PASS",
        f"release AAB requests PAGE_ALIGNMENT_16K and {len(libraries)} arm64-v8a/x86_64 libraries pass ELF alignment",
    )


def android_sdk_tool(name: str) -> str | None:
    binary = shutil.which(name)
    if binary:
        return binary

    sdk_roots = [
        Path(value).expanduser()
        for value in (
            os.environ.get("ANDROID_HOME", ""),
            os.environ.get("ANDROID_SDK_ROOT", ""),
            str(Path.home() / "Library/Android/sdk"),
        )
        if value
    ]

    def version_key(path: Path) -> tuple[int, ...]:
        numbers = tuple(int(part) for part in re.findall(r"\d+", path.parent.name))
        return numbers or (0,)

    candidates = {
        candidate.resolve()
        for sdk_root in sdk_roots
        for candidate in (sdk_root / "build-tools").glob(f"*/{name}")
        if candidate.is_file()
    }
    if not candidates:
        return None
    return str(max(candidates, key=version_key))


def elf64_load_alignment(data: bytes) -> tuple[str, str]:
    """Validate PT_LOAD alignment for a 64-bit ELF shared library."""
    if len(data) < 64 or data[:4] != b"\x7fELF":
        return ("FAIL", "not a complete ELF file")
    if data[4] != 2:
        return ("SKIP", "not a 64-bit ELF file")
    if data[5] not in (1, 2):
        return ("FAIL", "unsupported ELF byte order")

    endian = "<" if data[5] == 1 else ">"
    try:
        program_offset = struct.unpack_from(f"{endian}Q", data, 32)[0]
        program_entry_size = struct.unpack_from(f"{endian}H", data, 54)[0]
        program_count = struct.unpack_from(f"{endian}H", data, 56)[0]
    except struct.error:
        return ("FAIL", "truncated ELF header")

    expected_entry_size = struct.calcsize(f"{endian}IIQQQQQQ")
    if program_entry_size < expected_entry_size:
        return ("FAIL", "invalid ELF program-header size")

    alignments: list[int] = []
    try:
        for index in range(program_count):
            values = struct.unpack_from(
                f"{endian}IIQQQQQQ",
                data,
                program_offset + index * program_entry_size,
            )
            if values[0] == 1:  # PT_LOAD
                alignments.append(values[-1])
    except struct.error:
        return ("FAIL", "truncated ELF program headers")

    if not alignments:
        return ("FAIL", "no PT_LOAD segments")
    smallest = min(alignments)
    if smallest < 0x4000:
        return ("FAIL", f"PT_LOAD alignment {smallest:#x} is below 0x4000")
    return ("PASS", f"minimum PT_LOAD alignment {smallest:#x}")


def apk_16kb_status(
    apk_path: Path,
    *,
    run_command=None,
    tool_finder=None,
) -> tuple[str, str]:
    """Check the two local parts of Android's 16 KB contract for a built APK."""
    try:
        with zipfile.ZipFile(apk_path) as archive:
            libraries = sorted(
                name
                for name in archive.namelist()
                if re.fullmatch(r"lib/(?:arm64-v8a|x86_64)/.+\.so", name)
            )
            failures: list[str] = []
            for name in libraries:
                status, detail = elf64_load_alignment(archive.read(name))
                if status != "PASS":
                    failures.append(f"{name}: {detail}")
    except (OSError, zipfile.BadZipFile, KeyError) as exc:
        return ("FAIL", f"APK inspection failed: {exc.__class__.__name__}")

    if failures:
        return ("FAIL", "; ".join(failures))

    finder = tool_finder or android_sdk_tool
    zipalign = finder("zipalign")
    if not zipalign:
        return (
            "MANUAL",
            f"{len(libraries)} 64-bit native libraries pass ELF alignment; zipalign is unavailable",
        )

    runner = run_command or subprocess.run
    try:
        result = runner(
            [zipalign, "-v", "-c", "-P", "16", "4", str(apk_path)],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
    except OSError:
        return ("MANUAL", "zipalign could not be executed")

    if result.returncode != 0:
        return ("FAIL", f"zipalign -P 16 failed with exit {result.returncode}")
    return (
        "PASS",
        f"{len(libraries)} arm64-v8a/x86_64 libraries pass ELF alignment and APK zip alignment",
    )


def java_tool(name: str) -> str | None:
    binary = shutil.which(name)
    if binary:
        return binary
    java_home = os.environ.get("JAVA_HOME", "").strip()
    candidate = Path(java_home) / "bin" / name if java_home else None
    return str(candidate) if candidate and candidate.is_file() else None


def certificate_sha256(output: str) -> str | None:
    match = re.search(
        r"SHA-?256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})",
        output,
        flags=re.IGNORECASE,
    )
    return match.group(1).replace(":", "").upper() if match else None


def certificate_subject(output: str) -> str:
    match = re.search(r"^(?:Owner|Subject):\s*(.+)$", output, flags=re.IGNORECASE | re.MULTILINE)
    return match.group(1).strip() if match else ""


def is_debug_or_test_signer(certificate_output: str, key_alias: str) -> bool:
    if key_alias.strip().lower() in {"androiddebugkey", "debug", "test", "testkey"}:
        return True
    subject = certificate_subject(certificate_output)
    return bool(
        re.search(
            r"(?:^|,)\s*CN\s*=\s*(?:Android\s+Debug|AndroidDebugKey|Debug|Test)(?:\s*,|$)",
            subject,
            flags=re.IGNORECASE,
        )
    )


def display_sha256(fingerprint: str) -> str:
    return ":".join(fingerprint[index:index + 2] for index in range(0, len(fingerprint), 2))


def aab_signature_status(
    aab_path: Path,
    *,
    signing_ready: bool,
    store_path: Path,
    store_password: str,
    key_alias: str,
    run_command=None,
    tool_finder=None,
) -> tuple[str, str]:
    if not signing_ready or not store_path.is_file() or not store_password or not key_alias:
        return (
            "FAIL",
            "UNTRUSTED: release AAB exists, but the current ANDROID_KEYSTORE_* upload-key configuration is incomplete",
        )

    runner = run_command or subprocess.run
    find_tool = tool_finder or java_tool
    jarsigner = find_tool("jarsigner")
    keytool = find_tool("keytool")
    if not jarsigner or not keytool:
        return ("FAIL", "UNTRUSTED: jarsigner/keytool is unavailable, so the configured upload-key signer cannot be proven")

    command_options = {
        "cwd": ROOT,
        "text": True,
        "stdout": subprocess.PIPE,
        "stderr": subprocess.STDOUT,
        "check": False,
    }
    try:
        verification = runner(
            [jarsigner, "-J-Duser.language=en", "-J-Duser.country=US", "-verify", "-certs", str(aab_path)],
            **command_options,
        )
    except OSError:
        return ("FAIL", "UNTRUSTED: jarsigner could not inspect the release AAB")

    verification_output = (verification.stdout or "").lower()
    if verification.returncode != 0 or "jar verified" not in verification_output or "jar is unsigned" in verification_output:
        return ("FAIL", f"UNTRUSTED: release AAB signature verification failed with exit {verification.returncode}")

    password_env_name = "NUGUL_READINESS_STORE_PASSWORD"
    keytool_env = os.environ.copy()
    keytool_env[password_env_name] = store_password
    try:
        keystore_certificate = runner(
            [
                keytool,
                "-J-Duser.language=en",
                "-J-Duser.country=US",
                "-list",
                "-v",
                "-keystore",
                str(store_path),
                "-alias",
                key_alias,
                "-storepass:env",
                password_env_name,
            ],
            env=keytool_env,
            **command_options,
        )
        aab_certificate = runner(
            [
                keytool,
                "-J-Duser.language=en",
                "-J-Duser.country=US",
                "-printcert",
                "-jarfile",
                str(aab_path),
            ],
            **command_options,
        )
    except OSError:
        return ("FAIL", "UNTRUSTED: keytool could not inspect the configured keystore or AAB signer")

    if keystore_certificate.returncode != 0:
        return ("FAIL", f"UNTRUSTED: configured keystore alias certificate lookup failed with exit {keystore_certificate.returncode}")
    if aab_certificate.returncode != 0:
        return ("FAIL", f"UNTRUSTED: release AAB signer certificate lookup failed with exit {aab_certificate.returncode}")

    keystore_fingerprint = certificate_sha256(keystore_certificate.stdout or "")
    aab_fingerprint = certificate_sha256(aab_certificate.stdout or "")
    if not keystore_fingerprint or not aab_fingerprint:
        return ("FAIL", "UNTRUSTED: SHA-256 certificate fingerprint was missing from keytool output")
    if is_debug_or_test_signer(aab_certificate.stdout or "", key_alias):
        return ("FAIL", "UNTRUSTED: Android Debug/Test signing certificate is not acceptable for a Play upload AAB")
    if keystore_fingerprint != aab_fingerprint:
        return (
            "FAIL",
            "UNTRUSTED: AAB signer SHA-256 does not match the configured upload-key alias certificate",
        )
    return (
        "PASS",
        f"release AAB signer matches configured upload key SHA-256 {display_sha256(aab_fingerprint)}",
    )


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


def oauth_client_state_contract(
    *,
    platform: str,
    android_store: str,
    android_repository: str,
    ios_api: str,
    ios_config: str,
    backend_repository: str,
    backend_success: str,
    backend_failure: str,
) -> tuple[str, str]:
    android_ready = all(
        token in android_store
        for token in (
            "KEY_PENDING_CODE_VERIFIER",
            "KEY_PENDING_CLIENT_STATE",
            "KEY_PENDING_CREATED_AT",
            "constantTimeEquals",
            "consumePendingOAuthRequest",
        )
    ) and all(
        token in android_repository
        for token in (
            "addQueryParameter(OAUTH_CLIENT_STATE_QUERY_PARAMETER",
            "consumePendingOAuthRequest",
            "OAUTH_PENDING_REQUEST_TTL_MILLIS",
        )
    )
    ios_ready = all(
        token in ios_api
        for token in (
            "PendingOAuthTransactionStore",
            "static func constantTimeEquals",
            "kSecAttrAccessibleWhenUnlockedThisDeviceOnly",
            "func claim(",
            "clientState:",
        )
    ) and 'URLQueryItem(name: "client_state"' in ios_config
    backend_ready = all(
        token in backend_repository
        for token in (
            "CLIENT_STATE_PARAM_NAME",
            "CLIENT_STATE_PATTERN",
            "getClientState",
            "transferMetadataValue",
        )
    ) and all(
        "CLIENT_STATE_PARAM_NAME" in handler and "queryParam" in handler
        for handler in (backend_success, backend_failure)
    )

    native_by_platform = {
        "android": ("Android", android_ready),
        "ios": ("iOS", ios_ready),
    }
    if platform not in native_by_platform:
        raise ValueError(f"unsupported OAuth contract platform: {platform}")
    native_name, native_ready = native_by_platform[platform]
    missing = [name for name, ready in ((native_name, native_ready), ("backend", backend_ready)) if not ready]
    if missing:
        return ("FAIL", f"OAuth client_state round trip is incomplete: {', '.join(missing)}")
    return (
        "PASS",
        f"{native_name} and backend bind encrypted pending PKCE state to both success and failure callbacks",
    )


def backend_cookie_mutation_security_contract(
    cookie_filter: str,
    security_config: str,
    production_config: str,
    production_compose: str,
) -> tuple[str, str]:
    filter_ready = all(
        token in cookie_filter
        for token in (
            "AUTH_COOKIE_NAMES",
            "allowedOrigins",
            "hasBearerAuthorization",
            "SC_FORBIDDEN",
        )
    )
    chain_ready = all(
        token in security_config
        for token in ("CookieMutationOriginFilter", "csrfEnabled", "addFilterBefore")
    )
    prod_ready = bool(re.search(
        r"app:\s.*?security:\s.*?csrf:\s.*?enabled:\s*true",
        production_config,
        flags=re.DOTALL,
    )) and 'APP_SECURITY_CSRF_ENABLED: "true"' in production_compose
    if filter_ready and chain_ready and prod_ready:
        return (
            "PASS",
            "Browser cookie mutations require an allowed Origin/Referer while native bearer requests remain supported",
        )
    return (
        "FAIL",
        "Production cookie-authenticated mutations are missing an enabled same-origin protection contract",
    )


def backend_error_redaction_contract(*controller_sources: str) -> tuple[str, str]:
    leaked_response_patterns = (
        r'"message"\s*,\s*"[^"]*"\s*\+\s*e\.getMessage\(\)',
        r'\.body\([^\n]*e\.getMessage\(\)',
        r'throw\s+new\s+RuntimeException\([^\n]*e\.getMessage\(\)',
    )
    if any(
        re.search(pattern, source)
        for source in controller_sources
        for pattern in leaked_response_patterns
    ):
        return ("FAIL", "Production auth/profile responses concatenate internal exception messages")
    return ("PASS", "Production auth/profile failures return fixed user-safe messages")


def backend_sensitive_rate_limit_contract(
    rate_limit_filter: str,
    security_config: str,
    production_config: str,
    production_compose: str,
) -> tuple[str, str]:
    filter_ready = all(
        token in rate_limit_filter
        for token in (
            "AUTH_POST_PATHS",
            'path.startsWith("/operator/")',
            "request.getRemoteAddr()",
            'response.setStatus(429)',
            'response.setHeader("Retry-After"',
            "maxTrackedClients",
        )
    )
    chain_ready = all(
        token in security_config
        for token in (
            "SensitiveEndpointRateLimitFilter",
            "sensitiveRateLimitEnabled",
            "sensitiveRequestsPerWindow",
            "sensitiveWindowSeconds",
            "addFilterBefore",
        )
    )
    prod_ready = bool(re.search(
        r"sensitive-rate-limit:\s.*?enabled:\s*true",
        production_config,
        flags=re.DOTALL,
    )) and 'APP_SECURITY_SENSITIVE_RATE_LIMIT_ENABLED: "true"' in production_compose
    if filter_ready and chain_ready and prod_ready:
        return (
            "PASS",
            "Credential exchange and operator endpoints have a bounded production IP rate limit",
        )
    return (
        "FAIL",
        "Sensitive production endpoints are missing an enabled bounded rate-limit contract",
    )


def ios_export_compliance_contract(ios_plist: str) -> tuple[str, str]:
    declared_exempt = bool(re.search(
        r"<key>\s*ITSAppUsesNonExemptEncryption\s*</key>\s*<false\s*/>",
        ios_plist,
        flags=re.DOTALL,
    ))
    if declared_exempt:
        return ("PASS", "Info.plist declares that the app does not use non-exempt encryption")
    return (
        "FAIL",
        "Export compliance declaration is missing or not classified as exempt",
    )


def ios_accessibility_audit_contract(
    *,
    ios_project: str,
    ios_scheme: str,
    ui_test_source: str,
    audit_script: str,
) -> tuple[str, str]:
    target_ready = all(
        token in ios_project
        for token in (
            "NeogulMapNativeUITests",
            "com.apple.product-type.bundle.ui-testing",
            "TEST_TARGET_NAME = NeogulMapNative",
        )
    )
    scheme_ready = all(
        token in ios_scheme
        for token in (
            'BlueprintName = "NeogulMapNativeUITests"',
            "TestableReference",
            'skipped = "NO"',
        )
    )
    runtime_audit_ready = all(
        token in ui_test_source
        for token in ("XCUIApplication", "performAccessibilityAudit")
    )
    phone_and_tablet_ready = all(
        token in audit_script
        for token in (
            "IOS_A11Y_PHONE_DESTINATION",
            "IOS_A11Y_TABLET_DESTINATION",
            "iPhone",
            "iPad",
        )
    ) and audit_script.count("xcodebuild") >= 2

    if target_ready and scheme_ready and runtime_audit_ready and phone_and_tablet_ready:
        return (
            "PASS",
            "XCUI accessibility audits are wired to the shared scheme and repeatable on iPhone and iPad simulators",
        )

    missing: list[str] = []
    if not target_ready:
        missing.append("UI test target")
    if not scheme_ready:
        missing.append("shared-scheme test registration")
    if not runtime_audit_ready:
        missing.append("performAccessibilityAudit runtime coverage")
    if not phone_and_tablet_ready:
        missing.append("iPhone/iPad execution matrix")
    return ("FAIL", "Missing " + ", ".join(missing))


def frontend_security_headers_contract(next_config: str) -> tuple[str, str]:
    required_tokens = (
        "poweredByHeader: false",
        "Strict-Transport-Security",
        "X-Content-Type-Options",
        "X-Frame-Options",
        "Referrer-Policy",
        "Permissions-Policy",
    )
    if all(token in next_config for token in required_tokens):
        return ("PASS", "Public policy/support pages have a production security-header baseline")
    return ("FAIL", "Frontend production security headers are incomplete")


def tobacco_positioning_contract(store_readiness: str, terms_page: str) -> tuple[str, str]:
    store_copy = store_readiness.casefold()
    store_ready = all(
        phrase in store_copy
        for phrase in (
            "does not sell, promote, or encourage tobacco or vaping",
            "secondhand-smoke exposure",
        )
    )
    terms_ready = all(
        phrase in terms_page
        for phrase in (
            "판매·홍보하거나",
            "흡연을 권장하지 않습니다",
            "간접흡연 노출 감소",
        )
    )
    if store_ready and terms_ready:
        return (
            "PASS",
            "Store review notes and public Terms consistently frame smoking-area data as non-promotional harm reduction",
        )
    return (
        "FAIL",
        "Smoking-area functionality lacks consistent non-promotion and harm-reduction context",
    )


def popup_tobacco_filter_contract(
    collector_source: str,
    backend_event_service: str,
) -> tuple[str, str]:
    def policy_section(source: str, start_marker: str, end_marker: str) -> str:
        start = source.find(start_marker)
        end = source.find(end_marker, start + len(start_marker)) if start >= 0 else -1
        return source[start:end] if start >= 0 and end > start else ""

    collector_ready = all(
        token in collector_source
        for token in (
            "TOBACCO_NICOTINE_PATTERN",
            "contains_tobacco_or_nicotine_terms(raw, source)",
            "not contains_tobacco_or_nicotine_terms(record)",
        )
    )
    backend_ready = all(
        token in backend_event_service
        for token in (
            "TOBACCO_NICOTINE_TERMS",
            ".filter(item -> !containsTobaccoOrNicotineTerms(item))",
            ".filter(event -> !containsTobaccoOrNicotineTerms(event))",
        )
    )
    required_product_aliases = ("iqos", "아이코스", "juul", "vaporizer")
    collector_policy = policy_section(
        collector_source,
        "TOBACCO_NICOTINE_PATTERN",
        "TOBACCO_METADATA_KEYS",
    ).casefold()
    backend_policy = policy_section(
        backend_event_service,
        "TOBACCO_NICOTINE_TERMS",
        "TOBACCO_METADATA_FIELDS",
    ).casefold()
    aliases_ready = all(
        alias in collector_policy and alias in backend_policy
        for alias in required_product_aliases
    )
    if collector_ready and backend_ready and aliases_ready:
        return (
            "PASS",
            "Popup collection and backend publication both block tobacco, nicotine, vape, IQOS, JUUL, and vaporizer metadata",
        )
    return (
        "FAIL",
        "Popup tobacco/nicotine filtering or required product aliases are missing from the collector or backend publication path",
    )


def android_location_permission_contract(manifest: str, map_ui: str, provider: str) -> tuple[str, str]:
    if "ACCESS_BACKGROUND_LOCATION" in manifest:
        return "FAIL", "Background location is outside the smoking map release scope"
    declared = any(name in manifest for name in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"))
    if not declared:
        return "FAIL", "Current-location feature needs foreground permission declarations"
    used = all(token in map_ui for token in (
        "RequestMultiplePermissions", "mapLocationPermissionLauncher.launch", "moveMapToCurrentLocationWithPermission"
    )) and all(token in provider for token in (
        "checkSelfPermission", "hasPermission()", "getLastKnownLocation", "ACCESS_COARSE_LOCATION"
    ))
    return ("PASS", "Optional foreground location has a permission-gated current-location implementation") if used else (
        "FAIL", "Declared location permissions lack a permission-gated implementation"
    )


def store_location_metadata_contract(
    store_readiness: str,
    platform: str,
) -> tuple[str, str]:
    required_by_platform = {
        "ios": (
            "App Store에 추가:",
            "위치 권한은 선택 사항",
            "Add to the App Store description:",
            "iOS location permission is optional",
        ),
        "android": (
            "Google Play에 추가:",
            "Android 위치 권한은 선택 사항",
            "Add to the Google Play description:",
            "Android location permission is optional",
        ),
    }
    if platform not in required_by_platform:
        raise ValueError(f"unsupported store metadata platform: {platform}")

    if all(phrase in store_readiness for phrase in required_by_platform[platform]):
        return (
            "PASS",
            "Store location copy matches the native package permission behavior",
        )
    return (
        "FAIL",
        "Store location-permission copy does not match this native package",
    )


def public_text_filter_contract(
    user_service: str,
    zone_service: str,
    review_service: str,
) -> tuple[str, str]:
    def uses_review_policy(source: str) -> bool:
        return "ReviewContentPolicy" in source and bool(
            re.search(r"\b[A-Za-z_]\w*Policy\.ensureAllowed\(", source)
        )

    user_ready = uses_review_policy(user_service) and all(
        token in user_service
        for token in ("validatePublicNickname", "NICKNAME_CONTENT_REJECTED")
    )
    if user_ready and uses_review_policy(zone_service) and uses_review_policy(review_service):
        return (
            "PASS",
            "Server-side content policy covers public nicknames, place text, and reviews",
        )
    return ("FAIL", "Public UGC text filtering is incomplete")


def ios_ugc_moderation_contract(
    *,
    ios_api: str,
    ios_zone_detail: str,
) -> tuple[str, str]:
    api_ready = all(
        token in ios_api
        for token in (
            '/api/zones/\\(zoneID)/reports',
            '/api/zones/\\(zoneID)/reviews/\\(reviewID)/reports',
            '/api/users/\\(authorID)/block',
        )
    )
    controls_ready = all(
        token in ios_zone_detail
        for token in (
            'Text("장소 신고")',
            'Text("신고")',
            'Text("차단")',
            "private func submitZoneReport",
            "private func submitReport",
            "private func submitBlock",
        )
    )
    if api_ready and controls_ready:
        return (
            "PASS",
            "Place/photo report, review report, and author-block controls are wired",
        )
    return ("FAIL", "UGC moderation controls or endpoints are incomplete")


def social_connection_management_contract(
    platform: str,
    android_map_ui: str,
    ios_settings: str,
    connected_accounts_page: str,
) -> tuple[str, str]:
    if platform == "android":
        native_ready = "LOGIN_CONNECTIONS_URL" in android_map_ui and "로그인 연결 관리" in android_map_ui
        required_providers = (
            "myaccount.google.com/connections",
            "accounts.kakao.com",
            "nid.naver.com",
        )
    elif platform == "ios":
        native_ready = "AppConfig.connectedAccountsURL" in ios_settings
        required_providers = (
            "account.apple.com",
            "myaccount.google.com/connections",
            "accounts.kakao.com",
            "nid.naver.com",
        )
    else:
        raise ValueError(f"unsupported connection-management platform: {platform}")

    if native_ready and all(provider in connected_accounts_page for provider in required_providers):
        if platform == "ios":
            return (
                "MANUAL",
                "Provider controls are discoverable, but real credential revocation and data-access termination require an App Review account/device smoke",
            )
        return ("PASS", "Native settings link to provider-specific social connection controls")
    return ("FAIL", "Social login connection/revocation guidance is incomplete")


def apple_account_deletion_revocation_contract(
    *,
    entitlements: str,
    ios_project: str,
    user_service: str,
    revocation_gate: str,
    token_endpoint: str,
    production_config: str,
    production_compose: str,
) -> tuple[str, str]:
    entitlement_ready = (
        "com.apple.developer.applesignin" in entitlements
        and "CODE_SIGN_ENTITLEMENTS" in ios_project
        and "NeogulMapNative.entitlements" in ios_project
    )

    start = user_service.find("deleteUser(")
    end = user_service.find("public record AccountDeletionResult", start)
    deletion_method = user_service[start:end] if start >= 0 and end > start else ""
    revoke_index = deletion_method.find("linkedAccountRevocationService.revokeBeforeDeletion(user)")
    local_delete_index = deletion_method.find("userRepository.deleteById(id)")
    retry_safe = (
        revoke_index >= 0
        and local_delete_index > revoke_index
        and "catch (BusinessBaseException" not in deletion_method[revoke_index:local_delete_index]
        and "manualAppleRevocationRequired = true" not in deletion_method[revoke_index:local_delete_index]
    )

    encrypted_token_ready = all(
        token in revocation_gate
        for token in ("refreshTokenCipher.decrypt", "revokeRefreshToken")
    ) and (
        "getAppleRefreshTokenCiphertext" in revocation_gate
        or "appleRefreshTokenCiphertext" in revocation_gate
    )
    endpoint_ready = all(
        token in token_endpoint
        for token in ("/auth/revoke", "revokeRefreshToken", "token_type_hint", "refresh_token")
    )
    production_ready = bool(
        re.search(r"revocation\s*:\s*.*?enabled\s*:\s*true", production_config, re.DOTALL)
    ) and 'APPLE_REVOCATION_ENABLED: "true"' in production_compose

    if not entitlement_ready:
        return ("FAIL", "Sign in with Apple entitlement is not wired to the iOS target")
    if not retry_safe:
        return ("FAIL", "Apple revocation failure can lose the encrypted token instead of preserving a retry path")
    if not encrypted_token_ready or not endpoint_ready:
        return ("FAIL", "Encrypted Apple refresh-token storage or /auth/revoke integration is incomplete")
    if not production_ready:
        return ("FAIL", "Apple token revocation is not forced on in production configuration")
    return (
        "PASS",
        "Apple refresh token is encrypted and revoked before local deletion; failures preserve the account for retry",
    )


def local_secret_permission_contract(path: Path) -> tuple[str, str]:
    if not path.exists():
        return ("PASS", f"Optional local secret file is absent: {path.name}")
    mode = path.stat().st_mode & 0o777
    if mode & 0o077 == 0:
        return ("PASS", f"Local secret file permissions are restricted ({mode:04o})")
    return ("FAIL", f"Local secret file is readable by group/others ({mode:04o})")


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    checks: list[Check] = []

    android_build = read("android-native/app/build.gradle.kts")
    android_manifest = read("android-native/app/src/main/AndroidManifest.xml")
    android_libs = read("android-native/gradle/libs.versions.toml")
    android_api = read("android-native/app/src/main/java/com/nugulmap/nativeapp/data/api/NugulApiService.kt")
    android_map_ui = read("android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt")
    android_props = read_properties(ROOT / "android-native/local.properties")
    ios_project = read("ios-native/NeogulMapNative.xcodeproj/project.pbxproj")
    ios_scheme = read(
        "ios-native/NeogulMapNative.xcodeproj/xcshareddata/xcschemes/NeogulMapNative.xcscheme"
    )
    ios_ui_test_source = read("ios-native/UITests/NeogulMapNativeAccessibilityUITests.swift")
    ios_accessibility_audit_script = read("ios-native/scripts/run-accessibility-audits.sh")
    ios_release_config = read_optional("ios-native/Config/Release.xcconfig")
    ios_secrets_config = read_optional("ios-native/Config/Secrets.xcconfig")
    ios_plist = read("ios-native/NeogulMapNative/Info.plist")
    ios_entitlements = read("ios-native/NeogulMapNative/NeogulMapNative.entitlements")
    ios_app_config = read("ios-native/NeogulMapNative/AppConfig.swift")
    ios_api = read("ios-native/NeogulMapNative/Services/NugulAPIClient.swift")
    ios_login = read("ios-native/NeogulMapNative/Views/ZoneMapView.swift")
    ios_settings = read("ios-native/NeogulMapNative/Views/SettingsView.swift")
    ios_zone_detail = read("ios-native/NeogulMapNative/Views/ZoneDetailView.swift")
    connected_accounts_page = read("frontend/app/connected-accounts/page.tsx")
    terms_page = read("frontend/app/terms/page.tsx")
    frontend_next_config = read("frontend/next.config.mjs")
    backend_oauth_repository = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/oauth/"
        "OAuth2AuthorizationRequestBasedOnCookieRepository.java"
    )
    backend_oauth_success = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/oauth/OAuth2SuccessHandler.java"
    )
    backend_oauth_failure = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/oauth/OAuth2FailureHandler.java"
    )
    backend_cookie_filter = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/CookieMutationOriginFilter.java"
    )
    backend_sensitive_rate_limit_filter = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/SensitiveEndpointRateLimitFilter.java"
    )
    backend_security_config = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/config/security/SecurityConfig.java"
    )
    backend_production_config = read("backend/api-server/src/main/resources/application-prod.yml")
    production_compose = read("docker-compose.production.yml")
    backend_auth_controller = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/controller/AuthController.java"
    )
    backend_user_controller = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/controller/UserController.java"
    )
    backend_user_service = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/UserService.java"
    )
    backend_apple_revocation_gate = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/AppleLinkedAccountRevocationGate.java"
    )
    backend_apple_token_endpoint = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/AppleTokenEndpointService.java"
    )
    backend_zone_service = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/ZoneService.java"
    )
    backend_zone_review_service = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/ZoneReviewService.java"
    )
    backend_zone_controller = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/controller/ZoneController.java"
    )
    popup_collector = read("backend/data-scripts/scripts/collect_popup_trends.py")
    backend_event_service = read(
        "backend/api-server/src/main/java/com/neogulmap/neogul_map/service/EventInsightService.java"
    )

    target_sdk = match_int(r"targetSdk\s*=\s*(\d+)", android_build)
    if target_sdk is not None and target_sdk >= 36:
        add(checks, "PASS", "android", "target-sdk", f"targetSdk={target_sdk} satisfies current Play target API floor")
    else:
        add(checks, "FAIL", "android", "target-sdk", f"targetSdk={target_sdk}", "Set targetSdk >= 36 before Play submission")

    app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', android_build)
    add(checks, "PASS" if app_id else "FAIL", "android", "application-id", app_id.group(1) if app_id else "missing", "Confirm Play Console package id" if app_id else "Set applicationId")
    if app_id:
        add(
            checks,
            "MANUAL",
            "android",
            "play-console-package-ownership",
            f"Repository release id is {app_id.group(1)}; draft/internal Play records are not publicly verifiable",
            f"Confirm or reserve {app_id.group(1)} in the target Play Console account before creating the upload key",
        )

    version_code = match_int(r"versionCode\s*=\s*(\d+)", android_build)
    add(checks, "PASS" if version_code and version_code > 0 else "FAIL", "android", "version-code", f"versionCode={version_code}", "Increment before each upload")

    if "nugulmap.com" in android_build and "KAKAO_NATIVE_APP_KEY" in android_build:
        add(checks, "PASS", "android", "production-config-hooks", "API base and Kakao native key are build-configurable")
    else:
        add(checks, "FAIL", "android", "production-config-hooks", "missing API/key config", "Add production API and key injection")

    if prop_or_env(android_props, "KAKAO_NATIVE_APP_KEY"):
        add(checks, "PASS", "android", "kakao-native-key-local", "KAKAO_NATIVE_APP_KEY present in local.properties")
    else:
        add(checks, "FAIL", "android", "kakao-native-key-local", "No local production Kakao native key detected", "Set local.properties and verify Kakao console package/key hash on device")
    add(
        checks,
        "MANUAL",
        "android",
        "play-app-signing-provider-hash",
        "The Play app-signing certificate used on delivered APKs cannot be proven before Play App Signing is configured",
        "Register the Play app-signing certificate hash, not only the upload-key hash, with Kakao/OAuth providers and test a Play-installed build",
    )

    release_signing_names = [
        "ANDROID_KEYSTORE_PATH",
        "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS",
        "ANDROID_KEY_PASSWORD",
    ]
    release_signing_values = {name: env_or_prop(android_props, name) for name in release_signing_names}
    store_file = Path(release_signing_values["ANDROID_KEYSTORE_PATH"] or "MISSING_ANDROID_KEYSTORE_PATH.jks")
    store_path = store_file if store_file.is_absolute() else ROOT / "android-native" / store_file
    release_signing_ready = all(release_signing_values.values()) and store_path.is_file()
    if release_signing_ready:
        add(checks, "PASS", "android", "release-signing-config", "upload-key properties and external keystore are present")
    else:
        add(
            checks,
            "FAIL",
            "android",
            "release-signing-config",
            "upload-key signing properties are incomplete or absent",
            "Set ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD before release builds",
        )

    if all(token in android_manifest for token in ('android:scheme="nugulmap"', 'android:host="oauth"', 'android:path="/callback"')):
        add(checks, "PASS", "android", "oauth-deeplink", "nugulmap://oauth/callback registered")
    else:
        add(checks, "FAIL", "android", "oauth-deeplink", "callback intent-filter missing", "Register OAuth callback deeplink")

    android_icon_files = (
        ROOT / "android-native/app/src/main/res/mipmap-mdpi/ic_launcher.png",
        ROOT / "android-native/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        ROOT / "android-native/app/src/main/res/drawable-mdpi/ic_launcher_foreground.png",
    )
    android_icons_ready = all(path.exists() for path in android_icon_files) and all(
        token in android_manifest
        for token in ('android:icon="@mipmap/ic_launcher"',)
    )
    add(
        checks,
        "PASS" if android_icons_ready else "FAIL",
        "android",
        "launcher-icons",
        "legacy fallback and adaptive launcher icon are wired" if android_icons_ready else "launcher/adaptive icon wiring is incomplete",
        "Add store-ready launcher and adaptive icon assets" if not android_icons_ready else "",
    )

    backup_rules = read_optional("android-native/app/src/main/res/xml/backup_rules.xml")
    extraction_rules = read_optional("android-native/app/src/main/res/xml/data_extraction_rules.xml")
    encrypted_prefs_name = "nugul_auth_encrypted.xml"
    backup_protected = (
        'android:fullBackupContent="@xml/backup_rules"' in android_manifest
        and 'android:dataExtractionRules="@xml/data_extraction_rules"' in android_manifest
        and encrypted_prefs_name in backup_rules
        and encrypted_prefs_name in extraction_rules
    )
    add(
        checks,
        "PASS" if backup_protected else "FAIL",
        "android",
        "auth-backup-exclusion",
        "encrypted auth/PKCE preferences are excluded from cloud backup and device transfer" if backup_protected else "encrypted auth preferences can be backed up",
        "Exclude the encrypted auth preference file from both backup rule formats" if not backup_protected else "",
    )

    location_provider = read_optional("android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/CurrentLocationProvider.kt")
    location_status, location_detail = android_location_permission_contract(
        android_manifest, android_map_ui, location_provider
    )
    add(checks, location_status, "android", "permission-minimization", location_detail,
        "Wire optional foreground permission to current location and retain manual search" if location_status == "FAIL" else "")

    auth_store = read("android-native/app/src/main/java/com/nugulmap/nativeapp/core/auth/AuthTokenStore.kt")
    auth_repository = read("android-native/app/src/main/java/com/nugulmap/nativeapp/data/repository/AuthRepository.kt")
    pkce_persisted = all(
        token in auth_store
        for token in (
            "KEY_PENDING_CODE_VERIFIER",
            "KEY_PENDING_CLIENT_STATE",
            "KEY_PENDING_CREATED_AT",
            "consumePendingOAuthRequest",
        )
    ) and all(
        token in auth_repository
        for token in (
            "savePendingOAuthRequest",
            "consumePendingOAuthRequest",
            "OAUTH_PENDING_REQUEST_TTL_MILLIS",
        )
    )
    add(
        checks,
        "PASS" if pkce_persisted else "FAIL",
        "android",
        "oauth-pkce-process-recovery",
        "PKCE verifier is persisted in encrypted preferences across process recreation" if pkce_persisted else "PKCE verifier is only in process memory",
        "Persist pending PKCE verifier in excluded encrypted storage until successful exchange" if not pkce_persisted else "",
    )

    for platform in ("android", "ios"):
        oauth_state_status, oauth_state_detail = oauth_client_state_contract(
            platform=platform,
            android_store=auth_store,
            android_repository=auth_repository,
            ios_api=ios_api,
            ios_config=ios_app_config,
            backend_repository=backend_oauth_repository,
            backend_success=backend_oauth_success,
            backend_failure=backend_oauth_failure,
        )
        add(
            checks,
            oauth_state_status,
            platform,
            "oauth-client-state-roundtrip",
            oauth_state_detail,
            "Bind a CSPRNG client_state to the platform's encrypted pending transaction and echo it from both backend callback outcomes"
            if oauth_state_status != "PASS"
            else "",
        )

    cookie_security_status, cookie_security_detail = backend_cookie_mutation_security_contract(
        backend_cookie_filter,
        backend_security_config,
        backend_production_config,
        production_compose,
    )
    add(
        checks,
        cookie_security_status,
        "shared",
        "cookie-mutation-origin-protection",
        cookie_security_detail,
        "Protect production browser cookie mutations with an allowed-origin filter"
        if cookie_security_status != "PASS"
        else "",
    )

    error_redaction_status, error_redaction_detail = backend_error_redaction_contract(
        backend_auth_controller,
        backend_user_controller,
        backend_zone_controller,
    )
    add(
        checks,
        error_redaction_status,
        "shared",
        "production-error-redaction",
        error_redaction_detail,
        "Replace concatenated exception details with fixed client messages"
        if error_redaction_status != "PASS"
        else "",
    )

    sensitive_rate_limit_status, sensitive_rate_limit_detail = backend_sensitive_rate_limit_contract(
        backend_sensitive_rate_limit_filter,
        backend_security_config,
        backend_production_config,
        production_compose,
    )
    add(
        checks,
        sensitive_rate_limit_status,
        "shared",
        "sensitive-endpoint-rate-limit",
        sensitive_rate_limit_detail,
        "Rate-limit authentication exchange and operator endpoints in production"
        if sensitive_rate_limit_status != "PASS"
        else "",
    )

    frontend_headers_status, frontend_headers_detail = frontend_security_headers_contract(
        frontend_next_config
    )
    add(
        checks,
        frontend_headers_status,
        "shared",
        "frontend-security-headers",
        frontend_headers_detail,
        "Add HSTS, no-sniff, framing, referrer, and permissions policy headers"
        if frontend_headers_status != "PASS"
        else "",
    )

    secret_mode_status, secret_mode_detail = local_secret_permission_contract(
        ROOT / "backend/api-server/.env.local"
    )
    add(
        checks,
        secret_mode_status,
        "shared",
        "local-secret-file-permissions",
        secret_mode_detail,
        "Restrict backend/api-server/.env.local to mode 0600"
        if secret_mode_status != "PASS"
        else "",
    )

    release_aab = ROOT / "android-native/app/build/outputs/bundle/release/app-release.aab"
    if release_aab.exists():
        signature_status, signature_detail = aab_signature_status(
            release_aab,
            signing_ready=release_signing_ready,
            store_path=store_path,
            store_password=release_signing_values["ANDROID_KEYSTORE_PASSWORD"],
            key_alias=release_signing_values["ANDROID_KEY_ALIAS"],
        )
        add(
            checks,
            "PASS" if signature_status == "PASS" else "FAIL",
            "android",
            "release-aab-artifact",
            (
                f"trusted upload-key-signed artifact: {release_aab.relative_to(ROOT)}"
                if signature_status == "PASS"
                else f"UNTRUSTED artifact exists: {release_aab.relative_to(ROOT)}"
            ),
            "Configure the Play upload key and rebuild bundleRelease" if signature_status != "PASS" else "",
        )
        add(
            checks,
            signature_status,
            "android",
            "release-aab-signature",
            signature_detail,
            "Configure the Play upload key and rebuild bundleRelease" if signature_status != "PASS" else "",
        )
        with zipfile.ZipFile(release_aab) as archive:
            native_libs = [name for name in archive.namelist() if name.startswith("base/lib/") and name.endswith(".so")]
        if native_libs or "kakaoMap" in android_libs or "kakao-map" in android_libs:
            status, detail = bundle_alignment_status(release_aab)
            add(checks, status, "android", "16kb-page-size", detail, "Validate on 16KB Android 15+ device/Play pre-launch report" if status != "PASS" else "")
        else:
            add(checks, "PASS", "android", "16kb-page-size", "No native .so files detected in release AAB")
    else:
        add(checks, "FAIL", "android", "release-aab-artifact", "signed release AAB not found", "Configure signing, then run cd android-native && ./gradlew :app:bundleRelease")
        if "kakaoMap" in android_libs or "kakao-map" in android_libs:
            debug_apk = ROOT / "android-native/app/build/outputs/apk/debug/app-debug.apk"
            if debug_apk.exists():
                status, detail = apk_16kb_status(debug_apk)
                add(
                    checks,
                    status,
                    "android",
                    "16kb-page-size",
                    f"debug APK local contract: {detail}",
                    "Build the signed release AAB and confirm PAGE_ALIGNMENT_16K/Play pre-launch report" if status != "FAIL" else "Update the failing native SDK/library, rebuild, and retest",
                )
            else:
                add(checks, "MANUAL", "android", "16kb-page-size", "Native Kakao Maps dependency present; no APK or AAB artifact to inspect", "Build an APK locally, then validate the signed AAB with bundletool/Play pre-launch report")

    if "isMinifyEnabled = false" in android_build:
        add(
            checks,
            "PASS",
            "android",
            "release-minify-policy",
            "R8 minification is explicitly disabled for v1; minification is optional for Play submission",
        )

    apple_team_id = resolved_apple_team_id(ios_project, ios_release_config, ios_secrets_config)
    if apple_team_id:
        add(checks, "PASS", "ios", "development-team", "Release Apple Team ID resolves from local/CI configuration")
    else:
        add(checks, "MANUAL", "ios", "development-team", "Release DEVELOPMENT_TEAM is empty or unresolved", "Configure Apple Developer Team ID in ignored Secrets.xcconfig or CI settings")

    ios_kakao_key = (
        os.environ.get("KAKAO_NATIVE_APP_KEY", "").strip()
        or xcconfig_value("KAKAO_NATIVE_APP_KEY", ios_release_config, ios_secrets_config)
    )
    ios_kakao_key_ready = bool(ios_kakao_key) and ios_kakao_key not in {
        "$(KAKAO_NATIVE_APP_KEY)",
        "YOUR_KAKAO_NATIVE_APP_KEY",
    }
    add(
        checks,
        "PASS" if ios_kakao_key_ready else "FAIL",
        "ios",
        "kakao-native-key-local",
        "KAKAO_NATIVE_APP_KEY resolves from local or CI configuration" if ios_kakao_key_ready else "No production Kakao native key resolves for the iOS release configuration",
        "Set the key in ignored Secrets.xcconfig or CI and verify the bundle id in Kakao Developers" if not ios_kakao_key_ready else "",
    )

    code_sign_values = set(build_setting_values("CODE_SIGN_STYLE", ios_project))
    if code_sign_values:
        add(checks, "PASS", "ios", "code-sign-style", ", ".join(sorted(code_sign_values)))
    else:
        add(checks, "FAIL", "ios", "code-sign-style", "missing", "Configure signing style before archive")

    bundle = re.search(r"PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^;]+);", ios_project)
    add(checks, "PASS" if bundle else "FAIL", "ios", "bundle-id", bundle.group(1).strip() if bundle else "missing", "Register bundle id in App Store Connect" if bundle else "Set bundle id")
    if bundle:
        bundle_id = bundle.group(1).strip()
        add(
            checks,
            "MANUAL",
            "ios",
            "app-store-connect-bundle-ownership",
            f"Repository release id is {bundle_id}; draft/internal App Store Connect records are not publicly verifiable",
            f"Confirm or reserve {bundle_id} in the target Apple Developer/App Store Connect team before provisioning",
        )

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

    xcodebuild = shutil.which("xcodebuild")
    xcrun = shutil.which("xcrun")
    if xcodebuild and xcrun:
        xcode_result = subprocess.run(
            [xcodebuild, "-version"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        sdk_result = subprocess.run(
            [xcrun, "--sdk", "iphoneos", "--show-sdk-version"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        xcode_major = major_version(r"Xcode\s+(\d+)", xcode_result.stdout)
        sdk_major = major_version(r"^\s*(\d+)", sdk_result.stdout)
        toolchain_ready = (
            xcode_result.returncode == 0
            and sdk_result.returncode == 0
            and xcode_major is not None
            and sdk_major is not None
            and xcode_major >= 26
            and sdk_major >= 26
        )
        add(
            checks,
            "PASS" if toolchain_ready else "FAIL",
            "ios",
            "submission-sdk-toolchain",
            f"Xcode major={xcode_major}, iPhoneOS SDK major={sdk_major}",
            "Use Xcode 26 or later with the iOS 26 SDK before App Store upload" if not toolchain_ready else "",
        )
    else:
        add(
            checks,
            "MANUAL",
            "ios",
            "submission-sdk-toolchain",
            "xcodebuild/xcrun unavailable on this host",
            "Run submission builds on Xcode 26 or later with the iOS 26 SDK",
        )

    archive_verifier = ROOT / "ios-native/scripts/verify-release-archive-code.sh"
    archive_verifier_ready = archive_verifier.is_file() and os.access(archive_verifier, os.X_OK)
    add(
        checks,
        "PASS" if archive_verifier_ready else "FAIL",
        "ios",
        "unsigned-release-archive-verifier",
        "repeatable unsigned arm64 Release archive verifier is executable" if archive_verifier_ready else "unsigned Release archive verifier missing or not executable",
        "Add and run a generic iOS Release archive code verifier" if not archive_verifier_ready else "",
    )

    app_icon_contents_path = ROOT / "ios-native/NeogulMapNative/Assets.xcassets/AppIcon.appiconset/Contents.json"
    app_icon_ready = False
    if app_icon_contents_path.exists():
        try:
            icon_manifest = json.loads(app_icon_contents_path.read_text(encoding="utf-8"))
            icon_filenames = [image.get("filename", "") for image in icon_manifest.get("images", [])]
            app_icon_ready = bool(icon_filenames) and all(
                filename and (app_icon_contents_path.parent / filename).is_file()
                for filename in icon_filenames
            )
        except (OSError, json.JSONDecodeError):
            app_icon_ready = False
    add(
        checks,
        "PASS" if app_icon_ready else "FAIL",
        "ios",
        "app-icon",
        "AppIcon asset catalog references a real 1024px icon" if app_icon_ready else "AppIcon filename/image is missing",
        "Add a valid opaque 1024px App Store icon" if not app_icon_ready else "",
    )

    privacy_manifest = read_optional("ios-native/NeogulMapNative/PrivacyInfo.xcprivacy")
    privacy_manifest_ready = all(
        token in privacy_manifest
        for token in (
            "NSPrivacyAccessedAPICategoryUserDefaults",
            "CA92.1",
        )
    ) and all(
        token in ios_project
        for token in ("PrivacyInfo.xcprivacy", "PrivacyInfo.xcprivacy in Resources")
    )
    add(
        checks,
        "PASS" if privacy_manifest_ready else "FAIL",
        "ios",
        "privacy-manifest",
        "app privacy manifest includes UserDefaults reason CA92.1 and is copied as a resource" if privacy_manifest_ready else "PrivacyInfo.xcprivacy is missing, incomplete, or not in resources",
        "Add PrivacyInfo.xcprivacy with the app's required-reason API declarations" if not privacy_manifest_ready else "",
    )

    if apple_team_id:
        add(checks, "MANUAL", "ios", "archive-testflight", "Signing team exists, but App Store Connect archive/upload is account-gated", "Run generic iOS archive, validate, and upload/TestFlight with the Developer account")
    else:
        add(checks, "MANUAL", "ios", "archive-testflight", "Archive/TestFlight is blocked by missing Apple account signing configuration", "Set signing team/profiles, then run archive validation")

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

    location_scope_tokens = ("흡연구역",)
    if "NSLocationWhenInUseUsageDescription" in ios_plist and all(
        token in ios_plist for token in location_scope_tokens
    ):
        add(checks, "PASS", "ios", "location-purpose-string", "Location usage description covers nearby smoking zones")
    else:
        add(checks, "FAIL", "ios", "location-purpose-string", "Location purpose string is missing or does not describe every location-backed map category", "Describe nearby smoking zones in NSLocationWhenInUseUsageDescription")

    export_status, export_detail = ios_export_compliance_contract(ios_plist)
    add(
        checks,
        export_status,
        "ios",
        "export-compliance-declaration",
        export_detail,
        "Classify encryption use and set ITSAppUsesNonExemptEncryption before submission"
        if export_status != "PASS"
        else "",
    )

    accessibility_status, accessibility_detail = ios_accessibility_audit_contract(
        ios_project=ios_project,
        ios_scheme=ios_scheme,
        ui_test_source=ios_ui_test_source,
        audit_script=ios_accessibility_audit_script,
    )
    add(
        checks,
        accessibility_status,
        "ios",
        "runtime-accessibility-audit",
        accessibility_detail,
        "Wire performAccessibilityAudit into the shared UI-test scheme and run it on iPhone and iPad"
        if accessibility_status != "PASS"
        else "",
    )

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
    apple_revocation_status, apple_revocation_detail = apple_account_deletion_revocation_contract(
        entitlements=ios_entitlements,
        ios_project=ios_project,
        user_service=backend_user_service,
        revocation_gate=backend_apple_revocation_gate,
        token_endpoint=backend_apple_token_endpoint,
        production_config=backend_production_config,
        production_compose=production_compose,
    )
    add(
        checks,
        apple_revocation_status,
        "ios",
        "apple-account-deletion-revocation",
        apple_revocation_detail,
        "Revoke the stored Apple refresh token before deleting local data, and preserve it for retry on provider failure"
        if apple_revocation_status != "PASS"
        else "",
    )
    if ios_account_delete:
        add(checks, "PASS", "ios", "account-deletion-ux", "Discoverable iOS account deletion path detected")
    else:
        add(checks, "FAIL", "ios", "account-deletion-ux", "No discoverable iOS account deletion path detected", "Add in-app account deletion initiation under profile/settings before App Store submission")
    if android_account_delete:
        add(checks, "PASS", "android", "account-deletion-ux", "Discoverable Android account deletion path detected")
    else:
        add(checks, "FAIL", "android", "account-deletion-ux", "No discoverable Android account deletion path detected", "Add in-app account deletion initiation before Play submission")

    for platform in ("android", "ios"):
        connection_status, connection_detail = social_connection_management_contract(
            platform,
            android_map_ui,
            ios_settings,
            connected_accounts_page,
        )
        add(
            checks,
            connection_status,
            platform,
            "social-login-connection-management",
            connection_detail,
            (
                "Complete a real-account credential revocation/data-access termination smoke for App Review"
                if connection_status == "MANUAL"
                else "Expose provider-specific connection management from this native settings screen"
            )
            if connection_status != "PASS" else "",
        )

    android_moderation_ready = all(
        token in android_api
        for token in (
            'api/zones/{zoneId}/reports',
            'api/zones/{zoneId}/reviews/{reviewId}/reports',
            'api/users/{userId}/block',
        )
    ) and all(
        token in android_map_ui
        for token in ('Text("장소 신고")', 'Text("신고 접수")', 'Text("차단")')
    )
    android_ugc_consent_ready = all(
        token in android_map_ui
        for token in (
            "canSubmitUgc(",
            "canSubmitZoneUgc(",
            "policyAccepted",
            "이용약관과 커뮤니티 운영정책에 동의합니다",
        )
    )
    ios_moderation_status, ios_moderation_detail = ios_ugc_moderation_contract(
        ios_api=ios_api,
        ios_zone_detail=ios_zone_detail,
    )
    ios_ugc_consent_ready = all(
        token in ios_zone_detail
        for token in (
            "UgcPolicyConsentRow",
            "isReviewPolicyAccepted",
            "이용약관 및 커뮤니티 운영정책에 동의합니다",
        )
    ) and all(
        token in ios_login
        for token in ("UgcPolicyConsentRow", "isPolicyAccepted")
    )
    add(
        checks,
        "PASS" if android_moderation_ready else "FAIL",
        "android",
        "ugc-report-block",
        "Place/photo report, review report, and author-block controls are wired" if android_moderation_ready else "UGC moderation controls or endpoints are incomplete",
        "Wire authenticated place/review reporting and user blocking" if not android_moderation_ready else "",
    )
    add(
        checks,
        ios_moderation_status,
        "ios",
        "ugc-report-block",
        ios_moderation_detail,
        "Wire authenticated place/review reporting and user blocking" if ios_moderation_status != "PASS" else "",
    )
    add(
        checks,
        "PASS" if android_ugc_consent_ready else "FAIL",
        "android",
        "ugc-policy-consent",
        "Mandatory Terms/community-policy acceptance gates zone and review submission" if android_ugc_consent_ready else "UGC submission is not gated by explicit policy acceptance",
        "Require explicit Terms/community-policy acceptance before every UGC submission flow" if not android_ugc_consent_ready else "",
    )
    add(
        checks,
        "PASS" if ios_ugc_consent_ready else "FAIL",
        "ios",
        "ugc-policy-consent",
        "Mandatory Terms/community-policy acceptance gates zone and review submission" if ios_ugc_consent_ready else "UGC submission is not gated by explicit policy acceptance",
        "Require explicit Terms/community-policy acceptance before every UGC submission flow" if not ios_ugc_consent_ready else "",
    )

    public_text_filter_status, public_text_filter_detail = public_text_filter_contract(
        backend_user_service,
        backend_zone_service,
        backend_zone_review_service,
    )
    add(
        checks,
        public_text_filter_status,
        "shared",
        "ugc-public-text-filter",
        public_text_filter_detail,
        "Apply one server-side moderation policy before publishing nicknames, place text, and reviews"
        if public_text_filter_status != "PASS"
        else "",
    )

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
        "Kakao Maps SDK declared Other Data Types",
    )
    missing_sections = [section for section in required_privacy_sections if section not in privacy_inventory]
    missing_terms = [term for term in required_data_terms if term not in privacy_inventory]
    if privacy_inventory and not missing_sections and not missing_terms:
        add(checks, "PASS", "shared", "privacy-data-inventory", "Store privacy inventory covers Play, Apple, metadata, deletion, review risks, and core data types")
    else:
        detail = "missing " + ", ".join(missing_sections + missing_terms) if privacy_inventory else "docs/store-privacy-readiness.md missing"
        add(checks, "FAIL", "shared", "privacy-data-inventory", detail, "Create/update store privacy data inventory before submission")

    tobacco_status, tobacco_detail = tobacco_positioning_contract(privacy_inventory, terms_page)
    add(
        checks,
        tobacco_status,
        "shared",
        "tobacco-harm-reduction-positioning",
        tobacco_detail,
        "State in review notes and public Terms that the app does not sell or promote tobacco and is intended for designated-area compliance and secondhand-smoke reduction"
        if tobacco_status != "PASS"
        else "",
    )

    for platform in ("android", "ios"):
        location_copy_status, location_copy_detail = store_location_metadata_contract(
            privacy_inventory,
            platform,
        )
        add(
            checks,
            location_copy_status,
            platform,
            "platform-specific-location-metadata",
            location_copy_detail,
            "Describe optional foreground location and manual-search fallback for each native package"
            if location_copy_status != "PASS"
            else "",
        )

    public_url_pattern = re.compile(r"https://(?!support\.google\.com|developer\.apple\.com)[^\s)]+", re.I)
    public_urls = [
        url.rstrip("`.,;:)]}>")
        for url in public_url_pattern.findall(privacy_inventory)
    ]
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

    checks = checks_for_platform(checks, args.platform)
    print(f"Scope: {args.platform} (shared gates included)")
    statuses = {"PASS": 0, "MANUAL": 0, "FAIL": 0}
    for check in checks:
        statuses[check.status] = statuses.get(check.status, 0) + 1
        suffix = f" | action: {check.action}" if check.action else ""
        print(f"[{check.status}] {check.area}:{check.item} - {check.detail}{suffix}")

    print("\nSummary:", json.dumps(statuses, ensure_ascii=False, sort_keys=True))
    if statuses.get("FAIL", 0):
        print("Result: NOT PUBLIC-LAUNCH READY (expected until store/account/device blockers are resolved)")
        return 1
    else:
        print("Result: STATIC GATES CLEAR; continue with manual store/device gates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
