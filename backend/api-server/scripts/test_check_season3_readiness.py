import importlib.util
import json
import unittest
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory


SCRIPT_PATH = Path(__file__).with_name("check-season2-readiness.py")
SPEC = importlib.util.spec_from_file_location("season3_readiness", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
readiness = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(readiness)


class Season3ReadinessTest(unittest.TestCase):
    def verified_record(self) -> dict[str, object]:
        return {
            "id": "seoul-culture-seongsu-popup",
            "title": "성수 브랜드 팝업",
            "kind": "popup",
            "startDate": "2026-07-12",
            "endDate": "2026-07-20",
            "latitude": 37.5446,
            "longitude": 127.0557,
            "address": "서울 성동구 성수동 연무장길",
            "source": "SEOUL_CULTURE_API",
            "collectionMode": "NETWORK",
            "detailUrl": "https://culture.seoul.go.kr/events/seongsu-popup",
            "collectedAt": datetime.now(timezone.utc).isoformat(),
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
            "publicationPolicy": "allowed_with_attribution",
        }

    def test_accepts_only_complete_official_seongsu_popup(self) -> None:
        record = self.verified_record()

        self.assertTrue(readiness.is_verified_seongsu_popup_record(record))
        self.assertEqual([], readiness.popup_quality_errors([record], 24))

    def test_rejects_general_festival_manual_and_invalid_links(self) -> None:
        record = self.verified_record()

        self.assertFalse(
            readiness.is_verified_seongsu_popup_record(
                {**record, "source": "KTO_TOUR_API", "kind": "festival"}
            )
        )
        self.assertFalse(
            readiness.is_verified_seongsu_popup_record(
                {**record, "collectionMode": "MANUAL"}
            )
        )
        self.assertFalse(
            readiness.is_verified_seongsu_popup_record(
                {**record, "detailUrl": "http://127.0.0.1/private"}
            )
        )
        self.assertFalse(
            readiness.is_verified_seongsu_popup_record(
                {**record, "startDate": "2026-07-21", "endDate": "2026-07-20"}
            )
        )

    def test_live_record_gate_does_not_accept_kto_as_popup_fallback(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "popup-trends.json"
            path.write_text(
                json.dumps([{**self.verified_record(), "source": "KTO_TOUR_API"}]),
                encoding="utf-8",
            )
            self.assertFalse(readiness.has_public_event_api_records(path, 24))

            path.write_text(json.dumps([self.verified_record()]), encoding="utf-8")
            self.assertTrue(readiness.has_public_event_api_records(path, 24))

    def test_provider_probe_requires_an_actual_seongsu_popup_row(self) -> None:
        valid = {
            "TITLE": "성수 여름 팝업",
            "PLACE": "서울 성동구 연무장길",
            "STRTDATE": "2026-07-12 00:00:00.0",
            "END_DATE": "2026-07-20 00:00:00.0",
            "LAT": "37.5446",
            "LOT": "127.0557",
            "HMPG_ADDR": "https://culture.seoul.go.kr/events/seongsu-popup",
        }

        self.assertTrue(readiness.seoul_culture_probe_item_ok(valid))
        self.assertFalse(readiness.seoul_culture_probe_item_ok({**valid, "TITLE": "성수 음악회"}))
        self.assertFalse(
            readiness.seoul_culture_probe_item_ok(
                {**valid, "PLACE": "서울 강남구", "LAT": "37.5000", "LOT": "127.0200"}
            )
        )
        self.assertFalse(
            readiness.seoul_culture_probe_item_ok({**valid, "HMPG_ADDR": "http://localhost/private"})
        )


if __name__ == "__main__":
    unittest.main()
