from __future__ import annotations

import importlib.util
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-native-public-launch-readiness.py"
SPEC = importlib.util.spec_from_file_location("native_public_launch_readiness", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
READINESS = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = READINESS
SPEC.loader.exec_module(READINESS)


PRODUCTION_FINGERPRINT = "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
OTHER_FINGERPRINT = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"


def certificate_output(subject: str, fingerprint: str) -> str:
    return f"Owner: {subject}\nCertificate fingerprints:\n\t SHA256: {fingerprint}\n"


class FakeCommandRunner:
    def __init__(self, *, keystore_fingerprint: str, aab_fingerprint: str, aab_subject: str) -> None:
        self.keystore_fingerprint = keystore_fingerprint
        self.aab_fingerprint = aab_fingerprint
        self.aab_subject = aab_subject
        self.calls: list[list[str]] = []

    def __call__(self, command: list[str], **_kwargs) -> subprocess.CompletedProcess[str]:
        self.calls.append(command)
        if "-verify" in command:
            output = "jar verified.\n"
        elif "-list" in command:
            output = certificate_output("CN=NugulMap Upload, O=NugulMap, C=KR", self.keystore_fingerprint)
        elif "-printcert" in command:
            output = certificate_output(self.aab_subject, self.aab_fingerprint)
        else:
            raise AssertionError(f"Unexpected command: {command}")
        return subprocess.CompletedProcess(command, 0, stdout=output)


class AabSigningContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        root = Path(self.temp_dir.name)
        self.aab_path = root / "app-release.aab"
        self.store_path = root / "upload.jks"
        self.aab_path.touch()
        self.store_path.touch()

    def status(self, runner: FakeCommandRunner, *, signing_ready: bool = True, alias: str = "upload"):
        return READINESS.aab_signature_status(
            self.aab_path,
            signing_ready=signing_ready,
            store_path=self.store_path,
            store_password="not-printed",
            key_alias=alias,
            run_command=runner,
            tool_finder=lambda name: name,
        )

    def test_missing_signing_configuration_is_untrusted_without_inspecting_artifact(self) -> None:
        runner = FakeCommandRunner(
            keystore_fingerprint=PRODUCTION_FINGERPRINT,
            aab_fingerprint=PRODUCTION_FINGERPRINT,
            aab_subject="CN=NugulMap Upload, O=NugulMap, C=KR",
        )

        status, detail = self.status(runner, signing_ready=False)

        self.assertEqual("FAIL", status)
        self.assertIn("UNTRUSTED", detail)
        self.assertEqual([], runner.calls)

    def test_matching_production_upload_certificate_passes(self) -> None:
        runner = FakeCommandRunner(
            keystore_fingerprint=PRODUCTION_FINGERPRINT,
            aab_fingerprint=PRODUCTION_FINGERPRINT,
            aab_subject="CN=NugulMap Upload, O=NugulMap, C=KR",
        )

        status, detail = self.status(runner)

        self.assertEqual("PASS", status)
        self.assertIn("matches configured upload key", detail)
        self.assertEqual(3, len(runner.calls))
        self.assertNotIn("not-printed", " ".join(part for call in runner.calls for part in call))
        self.assertIn("-storepass:env", runner.calls[1])

    def test_mismatched_signer_is_untrusted(self) -> None:
        runner = FakeCommandRunner(
            keystore_fingerprint=PRODUCTION_FINGERPRINT,
            aab_fingerprint=OTHER_FINGERPRINT,
            aab_subject="CN=Different Upload, O=Other, C=KR",
        )

        status, detail = self.status(runner)

        self.assertEqual("FAIL", status)
        self.assertIn("does not match", detail)

    def test_android_debug_signer_is_rejected_even_when_fingerprint_matches(self) -> None:
        runner = FakeCommandRunner(
            keystore_fingerprint=PRODUCTION_FINGERPRINT,
            aab_fingerprint=PRODUCTION_FINGERPRINT,
            aab_subject="C=US, O=Android, CN=Android Debug",
        )

        status, detail = self.status(runner, alias="androiddebugkey")

        self.assertEqual("FAIL", status)
        self.assertIn("Android Debug/Test", detail)


def elf64_with_load_alignment(alignment: int) -> bytes:
    data = bytearray(64 + 56)
    data[:4] = b"\x7fELF"
    data[4] = 2
    data[5] = 1
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<H", data, 54, 56)
    struct.pack_into("<H", data, 56, 1)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 0, 0, 0, 0, 0, 0, alignment)
    return bytes(data)


class Android16KbContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.apk_path = Path(self.temp_dir.name) / "app-debug.apk"

    def write_apk(self, *, arm64_alignment: int, include_32_bit_legacy: bool = False) -> None:
        with zipfile.ZipFile(self.apk_path, "w") as archive:
            archive.writestr("lib/arm64-v8a/libnative.so", elf64_with_load_alignment(arm64_alignment))
            if include_32_bit_legacy:
                archive.writestr("lib/armeabi-v7a/liblegacy.so", b"not inspected for 64-bit Play gate")

    def test_aligned_64_bit_elf_and_zip_packaging_pass(self) -> None:
        self.write_apk(arm64_alignment=0x4000, include_32_bit_legacy=True)

        status, detail = READINESS.apk_16kb_status(
            self.apk_path,
            run_command=lambda command, **_kwargs: subprocess.CompletedProcess(command, 0, stdout="Verification successful"),
            tool_finder=lambda name: name,
        )

        self.assertEqual("PASS", status)
        self.assertIn("1 arm64-v8a/x86_64", detail)

    def test_unaligned_64_bit_elf_fails_before_zipalign(self) -> None:
        self.write_apk(arm64_alignment=0x1000)
        calls: list[list[str]] = []

        status, detail = READINESS.apk_16kb_status(
            self.apk_path,
            run_command=lambda command, **_kwargs: calls.append(command),
            tool_finder=lambda name: name,
        )

        self.assertEqual("FAIL", status)
        self.assertIn("below 0x4000", detail)
        self.assertEqual([], calls)

    def test_missing_zipalign_keeps_local_packaging_manual(self) -> None:
        self.write_apk(arm64_alignment=0x4000)

        status, detail = READINESS.apk_16kb_status(
            self.apk_path,
            tool_finder=lambda _name: None,
        )

        self.assertEqual("MANUAL", status)
        self.assertIn("zipalign is unavailable", detail)


class AndroidAab16KbContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.aab_path = Path(self.temp_dir.name) / "app-release.aab"

    def write_aab(self, libraries: dict[str, bytes]) -> None:
        with zipfile.ZipFile(self.aab_path, "w") as archive:
            archive.writestr("BundleConfig.pb", b"test-config")
            for name, data in libraries.items():
                archive.writestr(name, data)

    def status(
        self,
        *,
        bundletool_output: str = "compression: PAGE_ALIGNMENT_16K",
        bundletool_returncode: int = 0,
        command_finder=lambda: ["bundletool"],
    ):
        return READINESS.bundle_alignment_status(
            self.aab_path,
            run_command=lambda command, **_kwargs: subprocess.CompletedProcess(
                command,
                bundletool_returncode,
                stdout=bundletool_output,
            ),
            command_finder=command_finder,
        )

    def test_page_alignment_16k_and_aligned_arm64_and_x86_64_pass(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libarm.so": elf64_with_load_alignment(0x4000),
            "base/lib/x86_64/libx86.so": elf64_with_load_alignment(0x10000),
        })

        status, detail = self.status()

        self.assertEqual("PASS", status)
        self.assertIn("PAGE_ALIGNMENT_16K", detail)
        self.assertIn("2 arm64-v8a/x86_64", detail)

    def test_page_alignment_16k_does_not_hide_4kb_arm64_elf(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libnative.so": elf64_with_load_alignment(0x1000),
        })

        status, detail = self.status()

        self.assertEqual("FAIL", status)
        self.assertIn("base/lib/arm64-v8a/libnative.so", detail)
        self.assertIn("below 0x4000", detail)

    def test_page_alignment_16k_does_not_hide_4kb_x86_64_elf(self) -> None:
        self.write_aab({
            "base/lib/x86_64/libnative.so": elf64_with_load_alignment(0x1000),
        })

        status, detail = self.status()

        self.assertEqual("FAIL", status)
        self.assertIn("base/lib/x86_64/libnative.so", detail)
        self.assertIn("below 0x4000", detail)

    def test_one_unaligned_library_fails_the_entire_aab(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libaligned.so": elf64_with_load_alignment(0x4000),
            "base/lib/arm64-v8a/libunaligned.so": elf64_with_load_alignment(0x1000),
        })

        status, detail = self.status()

        self.assertEqual("FAIL", status)
        self.assertIn("libunaligned.so", detail)
        self.assertNotIn("libaligned.so:", detail)

    def test_page_alignment_4k_fails_even_when_every_elf_is_aligned(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libnative.so": elf64_with_load_alignment(0x4000),
        })

        status, detail = self.status(bundletool_output="compression: PAGE_ALIGNMENT_4K")

        self.assertEqual("FAIL", status)
        self.assertIn("did not report PAGE_ALIGNMENT_16K", detail)

    def test_bundletool_unavailable_is_manual_after_elfs_pass(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libnative.so": elf64_with_load_alignment(0x4000),
        })

        status, detail = self.status(command_finder=lambda: None)

        self.assertEqual("MANUAL", status)
        self.assertIn("bundletool is unavailable", detail)

    def test_bundletool_failure_is_manual_after_elfs_pass(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libnative.so": elf64_with_load_alignment(0x4000),
        })

        status, detail = self.status(bundletool_returncode=2, bundletool_output="invalid bundle")

        self.assertEqual("MANUAL", status)
        self.assertIn("exit 2", detail)

    def test_corrupt_aab_fails_before_bundletool(self) -> None:
        self.aab_path.write_bytes(b"not-a-zip")
        command_calls: list[list[str]] = []

        status, detail = READINESS.bundle_alignment_status(
            self.aab_path,
            run_command=lambda command, **_kwargs: command_calls.append(command),
            command_finder=lambda: ["bundletool"],
        )

        self.assertEqual("FAIL", status)
        self.assertIn("AAB inspection failed", detail)
        self.assertEqual([], command_calls)

    def test_truncated_64bit_library_fails(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libtruncated.so": b"\x7fELF\x02\x01",
        })

        status, detail = self.status()

        self.assertEqual("FAIL", status)
        self.assertIn("libtruncated.so", detail)
        self.assertIn("not a complete ELF file", detail)

    def test_32bit_legacy_library_outside_target_abis_is_ignored(self) -> None:
        self.write_aab({
            "base/lib/arm64-v8a/libnative.so": elf64_with_load_alignment(0x4000),
            "base/lib/armeabi-v7a/liblegacy.so": b"not inspected for the 64-bit Play gate",
        })

        status, detail = self.status()

        self.assertEqual("PASS", status)
        self.assertIn("1 arm64-v8a/x86_64", detail)

    def test_non_64bit_binary_in_64bit_abi_path_fails(self) -> None:
        elf32 = bytearray(64)
        elf32[:4] = b"\x7fELF"
        elf32[4] = 1
        elf32[5] = 1
        self.write_aab({
            "base/lib/arm64-v8a/libwrongclass.so": bytes(elf32),
        })

        status, detail = self.status()

        self.assertEqual("FAIL", status)
        self.assertIn("libwrongclass.so", detail)
        self.assertIn("not a 64-bit ELF file", detail)


class OAuthClientStateContractTest(unittest.TestCase):
    def contract(self, platform: str, **overrides):
        arguments = dict(
            platform=platform,
            android_store="KEY_PENDING_CODE_VERIFIER KEY_PENDING_CLIENT_STATE KEY_PENDING_CREATED_AT constantTimeEquals consumePendingOAuthRequest",
            android_repository="addQueryParameter(OAUTH_CLIENT_STATE_QUERY_PARAMETER consumePendingOAuthRequest OAUTH_PENDING_REQUEST_TTL_MILLIS",
            ios_api='PendingOAuthTransactionStore static func constantTimeEquals kSecAttrAccessibleWhenUnlockedThisDeviceOnly func claim( clientState:',
            ios_config='URLQueryItem(name: "client_state"',
            backend_repository="CLIENT_STATE_PARAM_NAME CLIENT_STATE_PATTERN getClientState transferMetadataValue",
            backend_success="CLIENT_STATE_PARAM_NAME queryParam",
            backend_failure="CLIENT_STATE_PARAM_NAME queryParam",
        )
        arguments.update(overrides)
        return READINESS.oauth_client_state_contract(**arguments)

    def test_each_native_and_backend_round_trip_passes_independently(self) -> None:
        android_status, android_detail = self.contract("android")
        ios_status, ios_detail = self.contract("ios")

        self.assertEqual("PASS", android_status)
        self.assertEqual("PASS", ios_status)
        self.assertIn("Android and backend", android_detail)
        self.assertIn("iOS and backend", ios_detail)

    def test_missing_backend_failure_echo_fails_the_round_trip(self) -> None:
        status, detail = self.contract("ios", backend_failure="queryParam only")

        self.assertEqual("FAIL", status)
        self.assertIn("backend", detail)

    def test_ios_lane_ignores_missing_android_state_store(self) -> None:
        status, _detail = self.contract("ios", android_store="", android_repository="")

        self.assertEqual("PASS", status)

    def test_android_lane_ignores_missing_ios_state_store(self) -> None:
        status, _detail = self.contract("android", ios_api="", ios_config="")

        self.assertEqual("PASS", status)


class SocialConnectionManagementContractTest(unittest.TestCase):
    def test_android_lane_does_not_require_ios_settings(self) -> None:
        status, _detail = READINESS.social_connection_management_contract(
            "android",
            "LOGIN_CONNECTIONS_URL 로그인 연결 관리",
            "",
            "myaccount.google.com/connections accounts.kakao.com nid.naver.com",
        )

        self.assertEqual("PASS", status)

    def test_ios_lane_does_not_require_android_settings(self) -> None:
        status, _detail = READINESS.social_connection_management_contract(
            "ios",
            "",
            "AppConfig.connectedAccountsURL",
            "account.apple.com myaccount.google.com/connections accounts.kakao.com nid.naver.com",
        )

        self.assertEqual("MANUAL", status)

    def test_platform_provider_link_gap_fails(self) -> None:
        status, _detail = READINESS.social_connection_management_contract(
            "android",
            "LOGIN_CONNECTIONS_URL 로그인 연결 관리",
            "",
            "myaccount.google.com/connections accounts.kakao.com",
        )

        self.assertEqual("FAIL", status)


class IOSUgcModerationContractTest(unittest.TestCase):
    def test_custom_label_buttons_and_authenticated_actions_pass(self) -> None:
        status, detail = READINESS.ios_ugc_moderation_contract(
            ios_api=(
                '/api/zones/\\(zoneID)/reports '
                '/api/zones/\\(zoneID)/reviews/\\(reviewID)/reports '
                '/api/users/\\(authorID)/block'
            ),
            ios_zone_detail=(
                'Text("장소 신고") Text("신고") Text("차단") '
                'private func submitZoneReport private func submitReport private func submitBlock'
            ),
        )

        self.assertEqual("PASS", status)
        self.assertIn("report", detail.lower())

    def test_labels_without_action_wiring_fail(self) -> None:
        status, _detail = READINESS.ios_ugc_moderation_contract(
            ios_api=(
                '/api/zones/\\(zoneID)/reports '
                '/api/zones/\\(zoneID)/reviews/\\(reviewID)/reports '
                '/api/users/\\(authorID)/block'
            ),
            ios_zone_detail='Text("장소 신고") Text("신고") Text("차단")',
        )

        self.assertEqual("FAIL", status)

    def test_missing_author_block_endpoint_fails(self) -> None:
        status, _detail = READINESS.ios_ugc_moderation_contract(
            ios_api=(
                '/api/zones/\\(zoneID)/reports '
                '/api/zones/\\(zoneID)/reviews/\\(reviewID)/reports'
            ),
            ios_zone_detail=(
                'Text("장소 신고") Text("신고") Text("차단") '
                'private func submitZoneReport private func submitReport private func submitBlock'
            ),
        )

        self.assertEqual("FAIL", status)


class AppleAccountDeletionRevocationContractTest(unittest.TestCase):
    def contract(self, **overrides):
        arguments = dict(
            entitlements="com.apple.developer.applesignin",
            ios_project="CODE_SIGN_ENTITLEMENTS = NeogulMapNative/NeogulMapNative.entitlements;",
            user_service="""
                public AccountDeletionResult deleteUser(Long id) {
                    linkedAccountRevocationService.revokeBeforeDeletion(user);
                    zoneReviewRepository.deleteByAuthorId(id);
                    userRepository.deleteById(id);
                }
                public record AccountDeletionResult(boolean manualAppleRevocationRequired) {}
            """,
            revocation_gate="appleRefreshTokenCiphertext refreshTokenCipher.decrypt encryptedRefreshToken revokeRefreshToken",
            token_endpoint="https://appleid.apple.com/auth/revoke revokeRefreshToken token_type_hint refresh_token",
            production_config="revocation:\n  enabled: true",
            production_compose='APPLE_REVOCATION_ENABLED: "true"',
        )
        arguments.update(overrides)
        return READINESS.apple_account_deletion_revocation_contract(**arguments)

    def test_complete_retry_safe_apple_revocation_contract_passes(self) -> None:
        status, detail = self.contract()

        self.assertEqual("PASS", status)
        self.assertIn("before local deletion", detail)

    def test_catching_revocation_failure_and_continuing_deletion_fails(self) -> None:
        status, detail = self.contract(user_service="""
            public AccountDeletionResult deleteUser(Long id) {
                try {
                    linkedAccountRevocationService.revokeBeforeDeletion(user);
                } catch (BusinessBaseException exception) {
                    manualAppleRevocationRequired = true;
                }
                userRepository.deleteById(id);
            }
            public record AccountDeletionResult(boolean manualAppleRevocationRequired) {}
        """)

        self.assertEqual("FAIL", status)
        self.assertIn("retry", detail)

    def test_disabled_production_revocation_fails(self) -> None:
        status, _detail = self.contract(production_config="revocation:\n  enabled: false")

        self.assertEqual("FAIL", status)


class PlatformSelectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.checks = [
            READINESS.Check("PASS", "android", "android-only", "ok"),
            READINESS.Check("FAIL", "ios", "ios-only", "blocked"),
            READINESS.Check("MANUAL", "shared", "shared-gate", "manual"),
        ]

    def test_android_lane_excludes_ios_but_keeps_shared_gates(self) -> None:
        selected = READINESS.checks_for_platform(self.checks, "android")

        self.assertEqual(["android-only", "shared-gate"], [check.item for check in selected])

    def test_ios_lane_excludes_android_but_keeps_shared_gates(self) -> None:
        selected = READINESS.checks_for_platform(self.checks, "ios")

        self.assertEqual(["ios-only", "shared-gate"], [check.item for check in selected])

    def test_all_lane_keeps_every_check(self) -> None:
        selected = READINESS.checks_for_platform(self.checks, "all")

        self.assertEqual(self.checks, selected)


class BackendWebSecurityContractTest(unittest.TestCase):
    def test_complete_cookie_origin_contract_passes(self) -> None:
        status, _detail = READINESS.backend_cookie_mutation_security_contract(
            "AUTH_COOKIE_NAMES allowedOrigins hasBearerAuthorization SC_FORBIDDEN",
            "CookieMutationOriginFilter csrfEnabled addFilterBefore",
            "app:\n  security:\n    csrf:\n      enabled: true",
            'APP_SECURITY_CSRF_ENABLED: "true"',
        )

        self.assertEqual("PASS", status)

    def test_disabled_production_contract_fails(self) -> None:
        status, _detail = READINESS.backend_cookie_mutation_security_contract(
            "AUTH_COOKIE_NAMES allowedOrigins hasBearerAuthorization SC_FORBIDDEN",
            "CookieMutationOriginFilter csrfEnabled addFilterBefore",
            "app:\n  security:\n    csrf:\n      enabled: false",
            'APP_SECURITY_CSRF_ENABLED: "false"',
        )

        self.assertEqual("FAIL", status)

    def test_exception_message_concatenation_fails_redaction_contract(self) -> None:
        status, _detail = READINESS.backend_error_redaction_contract(
            'Map.of("message", "internal: " + e.getMessage())'
        )

        self.assertEqual("FAIL", status)

    def test_runtime_exception_message_concatenation_fails_redaction_contract(self) -> None:
        status, _detail = READINESS.backend_error_redaction_contract(
            'throw new RuntimeException("parse failed: " + e.getMessage());'
        )

        self.assertEqual("FAIL", status)

    def test_fixed_error_message_passes_redaction_contract(self) -> None:
        status, _detail = READINESS.backend_error_redaction_contract(
            'Map.of("message", "잠시 후 다시 시도해 주세요.")'
        )

        self.assertEqual("PASS", status)

    def test_complete_sensitive_rate_limit_contract_passes(self) -> None:
        status, _detail = READINESS.backend_sensitive_rate_limit_contract(
            'AUTH_POST_PATHS path.startsWith("/operator/") request.getRemoteAddr() response.setStatus(429) response.setHeader("Retry-After" maxTrackedClients',
            "SensitiveEndpointRateLimitFilter sensitiveRateLimitEnabled sensitiveRequestsPerWindow sensitiveWindowSeconds addFilterBefore",
            "sensitive-rate-limit:\n  enabled: true",
            'APP_SECURITY_SENSITIVE_RATE_LIMIT_ENABLED: "true"',
        )

        self.assertEqual("PASS", status)

    def test_disabled_sensitive_rate_limit_contract_fails(self) -> None:
        status, _detail = READINESS.backend_sensitive_rate_limit_contract(
            'AUTH_POST_PATHS path.startsWith("/operator/") request.getRemoteAddr() response.setStatus(429) response.setHeader("Retry-After" maxTrackedClients',
            "SensitiveEndpointRateLimitFilter sensitiveRateLimitEnabled sensitiveRequestsPerWindow sensitiveWindowSeconds addFilterBefore",
            "sensitive-rate-limit:\n  enabled: false",
            'APP_SECURITY_SENSITIVE_RATE_LIMIT_ENABLED: "false"',
        )

        self.assertEqual("FAIL", status)


class IOSPlistSubmissionContractTest(unittest.TestCase):
    def test_exempt_encryption_declaration_passes(self) -> None:
        status, _detail = READINESS.ios_export_compliance_contract(
            "<key>ITSAppUsesNonExemptEncryption</key>\n<false/>"
        )

        self.assertEqual("PASS", status)

    def test_true_declaration_does_not_match_unrelated_false_value(self) -> None:
        status, _detail = READINESS.ios_export_compliance_contract(
            "<key>ITSAppUsesNonExemptEncryption</key>\n<true/>\n"
            "<key>UIApplicationSupportsMultipleScenes</key>\n<false/>"
        )

        self.assertEqual("FAIL", status)


class IOSAccessibilityAuditContractTest(unittest.TestCase):
    def complete_contract(self, **overrides):
        arguments = dict(
            ios_project=(
                'NeogulMapNativeUITests productType = "com.apple.product-type.bundle.ui-testing"; '
                'TEST_TARGET_NAME = NeogulMapNative;'
            ),
            ios_scheme=(
                'BlueprintName = "NeogulMapNativeUITests" '
                'TestableReference skipped = "NO"'
            ),
            ui_test_source=(
                'import XCTest\n'
                'final class NeogulMapNativeAccessibilityUITests: XCTestCase {\n'
                '  func testHomeAccessibilityAudit() throws {\n'
                '    let app = XCUIApplication()\n'
                '    app.launch()\n'
                '    try app.performAccessibilityAudit()\n'
                '  }\n'
                '}\n'
            ),
            audit_script=(
                'IOS_A11Y_PHONE_DESTINATION="platform=iOS Simulator,name=iPhone 17 Pro,OS=latest"\n'
                'IOS_A11Y_TABLET_DESTINATION="platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=latest"\n'
                'xcodebuild test -destination "$IOS_A11Y_PHONE_DESTINATION"\n'
                'xcodebuild test -destination "$IOS_A11Y_TABLET_DESTINATION"\n'
            ),
        )
        arguments.update(overrides)
        return READINESS.ios_accessibility_audit_contract(**arguments)

    def test_ui_test_target_audit_and_phone_tablet_matrix_pass(self) -> None:
        status, detail = self.complete_contract()

        self.assertEqual("PASS", status)
        self.assertIn("iPhone and iPad", detail)

    def test_static_accessibility_labels_without_runtime_audit_fail(self) -> None:
        status, _detail = self.complete_contract(
            ui_test_source='.accessibilityLabel("설정 열기")'
        )

        self.assertEqual("FAIL", status)

    def test_phone_only_audit_script_fails(self) -> None:
        status, _detail = self.complete_contract(
            audit_script=(
                'IOS_A11Y_PHONE_DESTINATION="platform=iOS Simulator,name=iPhone 17 Pro,OS=latest"\n'
                'xcodebuild test -destination "$IOS_A11Y_PHONE_DESTINATION"\n'
            )
        )

        self.assertEqual("FAIL", status)


class FrontendSecurityContractTest(unittest.TestCase):
    def test_complete_header_baseline_passes(self) -> None:
        status, _detail = READINESS.frontend_security_headers_contract(
            "poweredByHeader: false Strict-Transport-Security X-Content-Type-Options "
            "X-Frame-Options Referrer-Policy Permissions-Policy"
        )

        self.assertEqual("PASS", status)

    def test_missing_hsts_fails(self) -> None:
        status, _detail = READINESS.frontend_security_headers_contract(
            "poweredByHeader: false X-Content-Type-Options X-Frame-Options "
            "Referrer-Policy Permissions-Policy"
        )

        self.assertEqual("FAIL", status)


class TobaccoPositioningContractTest(unittest.TestCase):
    def test_harm_reduction_positioning_passes(self) -> None:
        status, _detail = READINESS.tobacco_positioning_contract(
            "NugulMap does not sell, promote, or encourage tobacco or vaping. "
            "Smoking-area information helps reduce secondhand-smoke exposure.",
            "너굴맵은 담배·전자담배를 판매·홍보하거나 흡연을 권장하지 않습니다. "
            "흡연구역 정보는 간접흡연 노출 감소를 돕기 위한 안내입니다.",
        )

        self.assertEqual("PASS", status)

    def test_smoking_location_copy_without_non_promotion_context_fails(self) -> None:
        status, _detail = READINESS.tobacco_positioning_contract(
            "Find smoking areas near you.",
            "주변 흡연구역을 지도에서 찾을 수 있습니다.",
        )

        self.assertEqual("FAIL", status)


class PopupTobaccoFilterContractTest(unittest.TestCase):
    @staticmethod
    def collector_policy() -> str:
        return (
            'TOBACCO_NICOTINE_PATTERN = re.compile("iqos|아이코스|juul|vaporizer")\n'
            "TOBACCO_METADATA_KEYS = {\"title\"}\n"
            "if contains_tobacco_or_nicotine_terms(raw, source): return None\n"
            "and not contains_tobacco_or_nicotine_terms(record)"
        )

    @staticmethod
    def backend_policy() -> str:
        return (
            'TOBACCO_NICOTINE_TERMS = List.of("iqos", "아이코스", "juul", "vaporizer");\n'
            "TOBACCO_METADATA_FIELDS = List.of(\"title\");\n"
            ".filter(item -> !containsTobaccoOrNicotineTerms(item))\n"
            ".filter(event -> !containsTobaccoOrNicotineTerms(event))"
        )

    def test_collector_and_backend_defense_in_depth_passes(self) -> None:
        status, _detail = READINESS.popup_tobacco_filter_contract(
            self.collector_policy(),
            self.backend_policy(),
        )

        self.assertEqual("PASS", status)

    def test_missing_backend_raw_metadata_gate_fails(self) -> None:
        status, _detail = READINESS.popup_tobacco_filter_contract(
            self.collector_policy(),
            self.backend_policy().replace(
                ".filter(item -> !containsTobaccoOrNicotineTerms(item))\n",
                "",
            ),
        )

        self.assertEqual("FAIL", status)

    def test_each_product_alias_is_required_in_both_policy_sections(self) -> None:
        for alias in ("iqos", "아이코스", "juul", "vaporizer"):
            with self.subTest(layer="collector", alias=alias):
                status, _detail = READINESS.popup_tobacco_filter_contract(
                    self.collector_policy().replace(alias, "missing-alias"),
                    self.backend_policy(),
                )
                self.assertEqual("FAIL", status)
            with self.subTest(layer="backend", alias=alias):
                status, _detail = READINESS.popup_tobacco_filter_contract(
                    self.collector_policy(),
                    self.backend_policy().replace(alias, "missing-alias"),
                )
                self.assertEqual("FAIL", status)


class StoreLocationMetadataContractTest(unittest.TestCase):
    def test_old_android_no_location_claim_is_rejected(self) -> None:
        status, _ = READINESS.store_location_metadata_contract(
            "Google Play에 추가: Android 앱은 기기 위치 권한을 요청하지 않으며\n"
            "Add to the Google Play description: The Android app does not request device location permission.",
            "android",
        )
        self.assertEqual("FAIL", status)

    def test_ios_copy_passes_without_android_copy(self) -> None:
        status, _detail = READINESS.store_location_metadata_contract(
            "App Store에 추가: 위치 권한은 선택 사항이며 거부해도 검색할 수 있습니다.\n"
            "Add to the App Store description: iOS location permission is optional.",
            "ios",
        )

        self.assertEqual("PASS", status)

    def test_android_copy_passes_without_ios_copy(self) -> None:
        status, _detail = READINESS.store_location_metadata_contract(
            "Google Play에 추가: Android 위치 권한은 선택 사항이며 검색과 지도 이동을 사용할 수 있습니다.\n"
            "Add to the Google Play description: Android location permission is optional.",
            "android",
        )

        self.assertEqual("PASS", status)

    def test_shared_permission_claim_without_platform_boundary_fails(self) -> None:
        status, _detail = READINESS.store_location_metadata_contract(
            "위치 권한은 선택 사항이며 허용하면 현재 위치 주변 장소를 보여줍니다.",
            "ios",
        )

        self.assertEqual("FAIL", status)

    def test_korean_split_with_ambiguous_english_copy_fails(self) -> None:
        status, _detail = READINESS.store_location_metadata_contract(
            "App Store에 추가: 위치 권한은 선택 사항입니다.\n"
            "Google Play에 추가: Android 앱은 기기 위치 권한을 요청하지 않으며 검색을 사용합니다.\n"
            "Location permission is optional and shows nearby places.",
            "android",
        )

        self.assertEqual("FAIL", status)


class PublicTextFilterContractTest(unittest.TestCase):
    def test_distinct_policy_field_names_still_pass(self) -> None:
        status, _detail = READINESS.public_text_filter_contract(
            "ReviewContentPolicy validatePublicNickname contentPolicy.ensureAllowed(value) NICKNAME_CONTENT_REJECTED",
            "ReviewContentPolicy contentPolicy.ensureAllowed(request.getDescription())",
            "ReviewContentPolicy reviewContentPolicy.ensureAllowed(normalizedContent)",
        )

        self.assertEqual("PASS", status)

    def test_missing_public_nickname_filter_fails(self) -> None:
        status, _detail = READINESS.public_text_filter_contract(
            "ReviewContentPolicy",
            "ReviewContentPolicy contentPolicy.ensureAllowed(request.getDescription())",
            "ReviewContentPolicy reviewContentPolicy.ensureAllowed(normalizedContent)",
        )

        self.assertEqual("FAIL", status)


class LocalSecretPermissionContractTest(unittest.TestCase):
    def test_private_file_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            secret = Path(temp_dir) / ".env.local"
            secret.touch()
            secret.chmod(0o600)

            status, _detail = READINESS.local_secret_permission_contract(secret)

        self.assertEqual("PASS", status)

    def test_world_readable_file_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            secret = Path(temp_dir) / ".env.local"
            secret.touch()
            secret.chmod(0o644)

            status, _detail = READINESS.local_secret_permission_contract(secret)

        self.assertEqual("FAIL", status)


if __name__ == "__main__":
    unittest.main()
