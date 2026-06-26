import unittest
import tempfile
import sys
from pathlib import Path

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_popup_trends import (
    collect,
    extract_embedded_json_records,
    extract_feed_records,
    extract_json_payload_records,
    extract_json_ld_events,
    expand_env_string,
    normalize_record,
    validate_record,
)
from run_popup_trend_collector import write_records_atomic


class CollectPopupTrendsTest(unittest.TestCase):
    def test_normalizes_manual_source_record(self):
        source = {
            "title": "성수 팝업 후보",
            "kind": "popup",
            "period": "이번 주",
            "latitude": 37.5446,
            "longitude": 127.0557,
            "address": "서울 성동구 성수동2가",
        }

        record = normalize_record(source, source)

        self.assertIsNotNone(record)
        self.assertEqual("성수 팝업 후보", record["title"])
        self.assertEqual("popup", record["kind"])
        self.assertTrue(validate_record(record))

    def test_extracts_json_ld_event(self):
        soup = BeautifulSoup(
            """
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "Event",
              "name": "잠실 여름 페스티벌",
              "startDate": "2026-07-01",
              "endDate": "2026-07-07",
              "location": {
                "name": "롯데월드타워",
                "geo": {"latitude": 37.5125, "longitude": 127.1028}
              },
              "url": "https://example.com/event"
            }
            </script>
            """,
            "html.parser",
        )

        records = extract_json_ld_events(soup, {"url": "https://example.com"})
        normalized = normalize_record(records[0], {})

        self.assertEqual(1, len(records))
        self.assertEqual("잠실 여름 페스티벌", normalized["title"])
        self.assertEqual(37.5125, normalized["latitude"])
        self.assertEqual(127.1028, normalized["longitude"])

    def test_extracts_next_data_event_candidate(self):
        soup = BeautifulSoup(
            """
            <script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "events": [
                    {
                      "title": "홍대 플리마켓",
                      "period": "주말",
                      "location": {
                        "geo": {"lat": 37.5563, "lng": 126.9236}
                      },
                      "address": "서울 마포구 홍대입구역"
                    }
                  ]
                }
              }
            }
            </script>
            """,
            "html.parser",
        )

        records = extract_embedded_json_records(soup, {"url": "https://example.com"})
        normalized = normalize_record(records[0], {})

        self.assertEqual(1, len(records))
        self.assertEqual("홍대 플리마켓", normalized["title"])
        self.assertEqual(37.5563, normalized["latitude"])
        self.assertEqual(126.9236, normalized["longitude"])

    def test_extracts_direct_json_payload_event_candidate(self):
        payload = {
            "items": [
                {
                    "name": "성수 브랜드 팝업",
                    "category": "popup",
                    "start_date": "2026-07-01",
                    "end_date": "2026-07-14",
                    "location": {
                        "geo": {"latitude": 37.5446, "longitude": 127.0557},
                    },
                    "address": "서울 성동구 성수동",
                    "link": "https://example.com/popups/seongsu",
                }
            ]
        }

        records = extract_json_payload_records(payload, {"url": "https://example.com/api"})
        normalized = normalize_record(records[0], {})

        self.assertEqual(1, len(records))
        self.assertEqual("성수 브랜드 팝업", normalized["title"])
        self.assertEqual("2026-07-01-2026-07-14", normalized["period"])
        self.assertEqual(37.5446, normalized["latitude"])
        self.assertEqual(127.0557, normalized["longitude"])

    def test_extracts_seoul_culture_event_openapi_payload(self):
        payload = {
            "culturalEventInfo": {
                "row": [
                    {
                        "CODENAME": "축제",
                        "TITLE": "잠실 문화행사",
                        "DATE": "2026-10-15~2026-10-15",
                        "PLACE": "롯데월드타워",
                        "STRTDATE": "2026-10-15 00:00:00.0",
                        "END_DATE": "2026-10-15 00:00:00.0",
                        "LOT": "127.1028",
                        "LAT": "37.5125",
                        "MAIN_IMG": "https://example.com/image.jpg",
                        "HMPG_ADDR": "https://culture.seoul.go.kr/event",
                    }
                ]
            }
        }

        source = {"url": "https://example.com/api", "kind": "event", "source": "SEOUL_CULTURE_API"}
        records = extract_json_payload_records(payload, source)
        normalized = normalize_record(records[0], source)

        self.assertEqual(1, len(records))
        self.assertEqual("잠실 문화행사", normalized["title"])
        self.assertEqual("축제", normalized["kind"])
        self.assertEqual("2026-10-15~2026-10-15", normalized["period"])
        self.assertEqual("2026-10-15", normalized["startDate"])
        self.assertEqual("2026-10-15", normalized["endDate"])
        self.assertEqual("롯데월드타워", normalized["address"])
        self.assertEqual(37.5125, normalized["latitude"])
        self.assertEqual(127.1028, normalized["longitude"])
        self.assertEqual("https://example.com/image.jpg", normalized["imageUrl"])
        self.assertEqual("SEOUL_CULTURE_API", normalized["source"])
        self.assertEqual("https://culture.seoul.go.kr/event", normalized["sourceContentId"])

    def test_expands_environment_placeholders_with_defaults(self):
        self.assertEqual(
            "http://openapi.seoul.go.kr:8088/sample/json/culturalEventInfo/1/100/",
            expand_env_string("http://openapi.seoul.go.kr:8088/${SEOUL_CULTURE_API_KEY:-sample}/json/culturalEventInfo/1/100/"),
        )
        self.assertEqual(
            "sample",
            expand_env_string("${SEOUL_CULTURE_API_KEY:-${SEOUL_CITYDATA_API_KEY:-sample}}"),
        )

    def test_normalizes_common_event_field_aliases(self):
        raw = {
            "displayName": "잠실 썸머 팝업",
            "eventStartDate": "2026-07-20",
            "eventEndDate": "2026-07-31",
            "place": {
                "placeName": "롯데월드몰",
                "geo": {"y": "37.5130", "x": "127.1035"},
            },
            "thumbnail": {"url": "https://example.com/thumb.jpg"},
            "href": "https://example.com/jamsil-popup",
        }

        record = normalize_record(raw, {"kind": "popup"})

        self.assertIsNotNone(record)
        self.assertEqual("잠실 썸머 팝업", record["title"])
        self.assertEqual("2026-07-20", record["startDate"])
        self.assertEqual("2026-07-31", record["endDate"])
        self.assertEqual("롯데월드몰", record["address"])
        self.assertEqual("https://example.com/thumb.jpg", record["imageUrl"])
        self.assertEqual("https://example.com/jamsil-popup", record["sourceContentId"])

    def test_validate_record_rejects_outside_seoul_coordinates(self):
        record = {
            "id": "popup-busan",
            "title": "부산 팝업",
            "kind": "popup",
            "latitude": 35.1796,
            "longitude": 129.0756,
            "source": "CRAWLED_POPUP_TREND",
        }

        self.assertFalse(validate_record(record))

    def test_extracts_rss_georss_event_candidate(self):
        soup = BeautifulSoup(
            """
            <rss version="2.0" xmlns:georss="http://www.georss.org/georss">
              <channel>
                <item>
                  <title>여의도 야외 축제</title>
                  <link>https://example.com/events/yeouido</link>
                  <pubDate>2026-08-01</pubDate>
                  <georss:point>37.5284 126.9327</georss:point>
                  <description>서울 영등포구 여의동로 330</description>
                </item>
              </channel>
            </rss>
            """,
            "xml",
        )

        records = extract_feed_records(soup, {"url": "https://example.com/rss", "kind": "festival"})
        normalized = normalize_record(records[0], {"kind": "festival"})

        self.assertEqual(1, len(records))
        self.assertEqual("여의도 야외 축제", normalized["title"])
        self.assertEqual("festival", normalized["kind"])
        self.assertEqual(37.5284, normalized["latitude"])
        self.assertEqual(126.9327, normalized["longitude"])

    def test_collect_skips_invalid_and_dedupes(self):
        config = {
            "sources": [
                {"title": "좌표 없는 후보"},
                {"title": "성수 팝업", "latitude": 37.5446, "longitude": 127.0557},
                {"title": "성수 팝업", "latitude": 37.5446, "longitude": 127.0557},
            ]
        }

        records = collect(config, timeout=1)

        self.assertEqual(1, len(records))
        self.assertEqual("성수 팝업", records[0]["title"])

    def test_collect_dedupes_same_event_across_different_source_urls(self):
        config = {
            "sources": [
                {
                    "manual": True,
                    "title": "성수 브랜드 팝업",
                    "startDate": "2026-07-01",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "url": "https://example.com/a",
                },
                {
                    "manual": True,
                    "title": "성수 브랜드 팝업",
                    "startDate": "2026-07-01",
                    "latitude": 37.54461,
                    "longitude": 127.05569,
                    "url": "https://example.com/b",
                },
            ]
        }

        records = collect(config, timeout=1)

        self.assertEqual(1, len(records))
        self.assertEqual("https://example.com/a", records[0]["sourceContentId"])

    def test_write_records_atomic_replaces_existing_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "popup-trends.json"
            output.write_text("[]", encoding="utf-8")

            write_records_atomic(output, [{"id": "popup-test", "title": "테스트 팝업"}])

            self.assertIn("테스트 팝업", output.read_text(encoding="utf-8"))
            self.assertFalse(output.with_suffix(".json.tmp").exists())


if __name__ == "__main__":
    unittest.main()
