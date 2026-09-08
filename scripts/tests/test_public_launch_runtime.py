from __future__ import annotations

import importlib.util
import sys
import unittest
from datetime import date, datetime, timezone
from pathlib import Path
from unittest.mock import patch
import io
from contextlib import redirect_stdout


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-public-launch-runtime.py"
SPEC = importlib.util.spec_from_file_location("public_launch_runtime", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
RUNTIME = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUNTIME
SPEC.loader.exec_module(RUNTIME)


class SmokingOnlyRuntimeTest(unittest.TestCase):
    def test_default_gate_checks_zones_and_probes_disabled_discovery_routes(self) -> None:
        calls = []

        def response(url, timeout):
            calls.append(url)
            return RUNTIME.Result("FAIL", url, "offline test"), None

        with patch.object(sys, "argv", ["check"]), patch.object(
            RUNTIME, "check_page", return_value=RUNTIME.Result("PASS", "page", "ok")
        ), patch.object(RUNTIME, "parse_json", side_effect=response), patch.object(
            RUNTIME, "check_discovery_disabled", return_value=RUNTIME.Result("PASS", "scope", "disabled")
        ) as disabled, redirect_stdout(io.StringIO()):
            RUNTIME.main()

        self.assertTrue(any("/zones/bounds?" in url for url in calls))
        self.assertTrue(any("/zones/search?" in url for url in calls))
        self.assertFalse(any(term in url for url in calls for term in (
            "/insights", "/recommendations", "/location-letters"
        )))
        self.assertEqual(3, disabled.call_count)

    def test_discovery_must_be_absent_not_merely_unauthorized_or_failing(self) -> None:
        for code in (200, 401, 403, 429, 500):
            with self.subTest(code=code), patch.object(RUNTIME, "request", return_value=(code, "", b"")):
                self.assertEqual("FAIL", RUNTIME.check_discovery_disabled("https://api.example/api", "/insights/status", 1).status)
        for code in (404, 410):
            with self.subTest(code=code), patch.object(RUNTIME, "request", return_value=(code, "", b"")):
                self.assertEqual("PASS", RUNTIME.check_discovery_disabled("https://api.example/api", "/insights/status", 1).status)

    def test_empty_launch_area_cannot_pass_as_usable_data(self) -> None:
        result = RUNTIME.check_zone_records({"success": True, "data": {"zones": []}})
        self.assertEqual("FAIL", result.status)

    def test_invalid_map_coordinate_or_pending_zone_fails(self) -> None:
        for invalid in ({"latitude": 190}, {"longitude": None}, {"publicationStatus": "PENDING"}):
            zone = {"id": 1, "latitude": 37.5, "longitude": 127.0,
                    "address": "서울 테스트 주소", "publicationStatus": "PUBLISHED"} | invalid
            with self.subTest(invalid=invalid):
                self.assertEqual("FAIL", RUNTIME.check_zone_records(
                    {"success": True, "data": {"zones": [zone]}}
                ).status)

    def test_all_known_demo_signatures_fail_even_with_different_ids(self) -> None:
        self.assertEqual(10, len(RUNTIME.KNOWN_DEMO_ZONES))
        for index, signature in enumerate(RUNTIME.KNOWN_DEMO_ZONES):
            zone = dict(signature, id=9000 + index, publicationStatus="PUBLISHED")
            self.assertEqual("FAIL", RUNTIME.check_zone_records(
                {"success": True, "data": {"zones": [zone]}}
            ).status)

    def test_modified_or_unrelated_places_are_not_guessed_to_be_demo_rows(self) -> None:
        demo = dict(RUNTIME.KNOWN_DEMO_ZONES[0], id=1)
        for changed in ({"address": "다른 실제 주소"}, {"latitude": 37.55},
                        {"longitude": 127.1}, {"description": "현장에서 확인한 안내"}):
            with self.subTest(changed=changed):
                self.assertEqual("PASS", RUNTIME.check_zone_records(
                    {"success": True, "data": {"zones": [demo | changed]}}
                ).status)

    def test_public_place_can_render_and_route(self) -> None:
        zone = {"id": 1, "latitude": 37.5, "longitude": 127.0, "address": "서울 테스트 주소"}
        self.assertEqual("PASS", RUNTIME.check_zone_records(
            {"success": True, "data": {"zones": [zone]}}
        ).status)

    def test_category_value_cannot_substitute_for_an_address(self) -> None:
        # The source audit found the literal category 실외 in final CSV row 752.
        for address in ('실외', ' 실내 ', '', '   '):
            with self.subTest(address=address):
                zone = {'id': 42, 'latitude': 37.5, 'longitude': 127.0, 'address': address}
                self.assertEqual('FAIL', RUNTIME.check_zone_records(
                    {'success': True, 'data': {'zones': [zone]}}
                ).status)


class PublicPolicyPageContractTest(unittest.TestCase):
    def test_policy_page_requires_the_expected_human_readable_content(self) -> None:
        unrelated_html = ("<html><body>unrelated landing page</body></html>" * 8).encode()

        with patch.object(
            RUNTIME,
            "request",
            return_value=(200, "text/html; charset=utf-8", unrelated_html),
        ):
            result = RUNTIME.check_page("https://nugulmap.com", "/privacy", 1.0)

        self.assertEqual("FAIL", result.status)
        self.assertIn("required content", result.detail)

    def test_policy_page_with_expected_content_passes(self) -> None:
        html = ("<html><body>너굴맵 개인정보 처리방침</body></html>" * 8).encode()

        with patch.object(
            RUNTIME,
            "request",
            return_value=(200, "text/html; charset=utf-8", html),
        ):
            result = RUNTIME.check_page("https://nugulmap.com", "/privacy", 1.0)

        self.assertEqual("PASS", result.status)


class ModerationContractTest(unittest.TestCase):
    def test_report_block_and_support_paths_are_required(self) -> None:
        payload = {
            "success": True,
            "data": {
                "supportEmailConfigured": True,
                "supportRequestPath": "/api/public/support/requests",
                "supportCategories": ["ACCOUNT_DELETION", "CONTENT_REPORT"],
                "reportPathTemplate": "/api/zones/{zoneId}/reviews/{reviewId}/reports",
                "blockPathTemplate": "/api/users/{userId}/block",
                "reportReasons": ["SPAM", "HARASSMENT"],
            },
        }

        self.assertEqual("PASS", RUNTIME.check_moderation_contract(payload).status)

        del payload["data"]["blockPathTemplate"]
        result = RUNTIME.check_moderation_contract(payload)

        self.assertEqual("FAIL", result.status)
        self.assertIn("blockPathTemplate", result.detail)

    def test_staffed_support_contact_is_required(self) -> None:
        payload = {
            "success": True,
            "data": {
                "supportEmailConfigured": False,
                "supportRequestPath": "/api/public/support/requests",
                "supportCategories": ["ACCOUNT_DELETION", "CONTENT_REPORT"],
                "reportPathTemplate": "/api/zones/{zoneId}/reviews/{reviewId}/reports",
                "blockPathTemplate": "/api/users/{userId}/block",
                "reportReasons": ["SPAM", "HARASSMENT"],
            },
        }

        result = RUNTIME.check_moderation_contract(payload)

        self.assertEqual("FAIL", result.status)
        self.assertIn("supportEmailConfigured=true", result.detail)


class SeongsuPopupContractTest(unittest.TestCase):
    def valid_payload(self) -> dict:
        return {
            "success": True,
            "data": {
                "events": {
                    "events": [
                        {
                            "id": "seongsu-popup-1",
                            "title": "성수 여름 팝업",
                            "kind": "popup",
                            "startDate": "2026-07-10",
                            "endDate": "2026-07-20",
                            "latitude": 37.544,
                            "longitude": 127.055,
                            "address": "서울 성동구 성수동 연무장길",
                            "source": "SEOUL_CULTURE_API",
                            "detailUrl": "https://example.com/popups/seongsu-1",
                            "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                            "attribution": "서울특별시 문화행사 정보",
                            "license": "공공누리 제1유형",
                            "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                        }
                    ],
                    "dataFreshness": "LIVE_OR_PARTIAL",
                    "sources": ["SEOUL_CULTURE_API"],
                },
                "status": {},
            },
        }

    def test_current_seongsu_popup_with_source_dates_and_link_passes(self) -> None:
        result = RUNTIME.check_insights_contract(
            self.valid_payload(),
            today=date(2026, 7, 12),
        )

        self.assertEqual("PASS", result.status)

    def test_expired_or_unverifiable_popup_fails(self) -> None:
        payload = self.valid_payload()
        popup = payload["data"]["events"]["events"][0]
        popup["endDate"] = "2026-07-11"
        popup["detailUrl"] = "javascript:alert(1)"

        result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

        self.assertEqual("FAIL", result.status)
        self.assertIn("events[0]", result.detail)

    def test_empty_popup_result_warns_when_live_provider_is_fresh_and_healthy(self) -> None:
        payload = self.valid_payload()
        payload["data"]["events"]["events"] = []
        payload["data"]["events"]["dataFreshness"] = "NO_VERIFIED_DATA"
        payload["data"]["status"] = {
            "eventMode": "LIVE_OR_CRAWLED_READY",
            "ktoTourApi": {"configured": False, "qualityStatus": "NOT_CONFIGURED"},
            "seoulCultureApi": {
                "configured": True,
                "qualityStatus": "OK",
                "lastSuccessAt": "2026-07-12T00:00:00Z",
            },
            "popupTrends": {
                "fileConfigured": True,
                "qualityStatus": "EMPTY",
                "recordCount": 0,
            },
        }

        result = RUNTIME.check_insights_contract(
            payload,
            today=date(2026, 7, 12),
            now=datetime(2026, 7, 12, 1, tzinfo=timezone.utc),
        )

        self.assertEqual("WARN", result.status)
        self.assertIn("empty UI is truthful", result.detail)

    def test_empty_popup_result_fails_when_provider_is_unconfigured(self) -> None:
        payload = self.valid_payload()
        payload["data"]["events"]["events"] = []
        payload["data"]["events"]["dataFreshness"] = "NO_VERIFIED_DATA"
        payload["data"]["status"] = {
            "eventMode": "NO_VERIFIED_DATA",
            "ktoTourApi": {"configured": False, "qualityStatus": "NOT_CONFIGURED"},
            "seoulCultureApi": {"configured": False, "qualityStatus": "NOT_CONFIGURED"},
            "popupTrends": {"fileConfigured": True, "qualityStatus": "EMPTY", "recordCount": 0},
        }

        result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

        self.assertEqual("FAIL", result.status)
        self.assertIn("no live event provider", result.detail)

    def test_empty_popup_result_fails_for_provider_error_or_stale_success(self) -> None:
        for quality_status, last_success_at, expected in (
            ("ERROR", "2026-07-12T00:00:00Z", "provider error"),
            ("OK", "2026-07-10T00:00:00Z", "stale"),
        ):
            with self.subTest(quality_status=quality_status):
                payload = self.valid_payload()
                payload["data"]["events"]["events"] = []
                payload["data"]["events"]["dataFreshness"] = "NO_VERIFIED_DATA"
                payload["data"]["status"] = {
                    "eventMode": "LIVE_OR_CRAWLED_READY",
                    "ktoTourApi": {"configured": False, "qualityStatus": "NOT_CONFIGURED"},
                    "seoulCultureApi": {
                        "configured": True,
                        "qualityStatus": quality_status,
                        "lastSuccessAt": last_success_at,
                    },
                    "popupTrends": {"fileConfigured": True, "qualityStatus": "EMPTY", "recordCount": 0},
                }

                result = RUNTIME.check_insights_contract(
                    payload,
                    today=date(2026, 7, 12),
                    now=datetime(2026, 7, 12, 1, tzinfo=timezone.utc),
                )

                self.assertEqual("FAIL", result.status)
                self.assertIn(expected, result.detail)

    def test_empty_popup_result_fails_if_freshness_claim_is_not_truthful(self) -> None:
        payload = self.valid_payload()
        payload["data"]["events"]["events"] = []

        result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

        self.assertEqual("FAIL", result.status)
        self.assertIn("dataFreshness=NO_VERIFIED_DATA", result.detail)

    def test_private_or_credentialed_detail_url_is_not_launch_ready(self) -> None:
        for url in (
            "http://127.0.0.1/private",
            "http://10.0.2.2/admin",
            "https://user:secret@example.com/private",
        ):
            with self.subTest(url=url):
                payload = self.valid_payload()
                payload["data"]["events"]["events"][0]["detailUrl"] = url

                result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

                self.assertEqual("FAIL", result.status)
                self.assertIn("detailUrl", result.detail)

    def test_unapproved_source_or_attribution_is_not_launch_ready(self) -> None:
        invalid_values = (
            ("source", "x"),
            ("attribution", "임의 출처"),
            ("attribution", "서울특별시"),
            ("attribution", "서울시 문화행사 정보"),
        )
        for key, value in invalid_values:
            with self.subTest(key=key):
                payload = self.valid_payload()
                payload["data"]["events"]["events"][0][key] = value

                result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

                self.assertEqual("FAIL", result.status)
                self.assertIn(key, result.detail)

    def test_popup_provenance_requires_exact_approved_urls_and_license(self) -> None:
        invalid_values = (
            ("sourceUrl", None),
            ("sourceUrl", "https://data.seoul.go.kr/other-dataset"),
            ("sourceUrl", "https://evil.example/dataList/OA-15486/S/1/datasetView.do"),
            ("license", "공공누리"),
            ("licenseUrl", None),
            ("licenseUrl", "https://www.kogl.or.kr/info/licenseType2.do"),
            ("licenseUrl", "https://evil.example/info/licenseType1.do"),
        )
        for key, value in invalid_values:
            with self.subTest(key=key, value=value):
                payload = self.valid_payload()
                payload["data"]["events"]["events"][0][key] = value

                result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

                self.assertEqual("FAIL", result.status)
                self.assertIn(key, result.detail)

    def test_tobacco_terms_in_source_metadata_or_link_are_not_launch_ready(self) -> None:
        invalid_values = (
            ("attribution", "아이코스 제공"),
            ("detailUrl", "https://example.com/vape/popup"),
            ("source", "TOBACCO_PARTNER"),
        )
        for key, value in invalid_values:
            with self.subTest(key=key):
                payload = self.valid_payload()
                payload["data"]["events"]["events"][0][key] = value

                result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

                self.assertEqual("FAIL", result.status)
                self.assertIn("tobacco", result.detail)

    def test_source_or_detail_metadata_cannot_supply_the_seongsu_location(self) -> None:
        for key, value in (
            ("detailUrl", "https://example.com/성수/popup"),
            ("sourceContentId", "성수-popup-1"),
        ):
            with self.subTest(key=key):
                payload = self.valid_payload()
                popup = payload["data"]["events"]["events"][0]
                popup["title"] = "여름 팝업"
                popup["address"] = "서울 성동구 응봉동"
                popup["detailUrl"] = "https://example.com/popups/1"
                popup["sourceContentId"] = "popup-1"
                popup[key] = value

                result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

                self.assertEqual("FAIL", result.status)
                self.assertIn("Seongsu", result.detail)

    def test_explicit_location_field_can_identify_seongsu(self) -> None:
        payload = self.valid_payload()
        popup = payload["data"]["events"]["events"][0]
        popup["title"] = "여름 팝업"
        popup["address"] = "서울 성동구"
        popup["location"] = "성수동 연무장길"

        result = RUNTIME.check_insights_contract(payload, today=date(2026, 7, 12))

        self.assertEqual("PASS", result.status)


class PublicUrlContractTest(unittest.TestCase):
    def test_legacy_numeric_ipv4_private_and_loopback_forms_are_rejected(self) -> None:
        for url in (
            "http://2130706433/private",
            "http://0177.0.0.1/private",
            "http://0x7f000001/private",
            "http://0300.0250.0001.0001/private",
        ):
            with self.subTest(url=url):
                self.assertFalse(RUNTIME.is_public_http_url(url))

    def test_ipv4_mapped_ipv6_is_rejected(self) -> None:
        for url in (
            "http://[::ffff:127.0.0.1]/private",
            "https://[::ffff:8.8.8.8]/mapped",
        ):
            with self.subTest(url=url):
                self.assertFalse(RUNTIME.is_public_http_url(url))

    def test_carrier_grade_nat_range_is_rejected(self) -> None:
        for url in (
            "http://100.64.0.1/private",
            "http://1681915905/private",
        ):
            with self.subTest(url=url):
                self.assertFalse(RUNTIME.is_public_http_url(url))


class PublicApiShapeContractTest(unittest.TestCase):
    def test_health_requires_up_object(self) -> None:
        self.assertEqual("FAIL", RUNTIME.check_health_contract([]).status)
        self.assertEqual("FAIL", RUNTIME.check_health_contract({}).status)
        self.assertEqual("PASS", RUNTIME.check_health_contract({"status": "UP"}).status)

    def test_zone_response_requires_success_data_and_zone_list(self) -> None:
        self.assertEqual("FAIL", RUNTIME.check_zone_privacy({}).status)
        self.assertEqual(
            "PASS",
            RUNTIME.check_zone_privacy({"success": True, "data": {"zones": []}}).status,
        )


if __name__ == "__main__":
    unittest.main()
