import unittest
import tempfile
import sys
import json
import os
from datetime import date, datetime, timezone
from pathlib import Path
from unittest.mock import Mock, patch

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_popup_trends import (
    CollectionBatch,
    DEFAULT_SOURCE_POLICIES,
    SourceFetchResult,
    collect,
    collect_due_sources,
    extract_korean_date_range,
    extract_embedded_json_records,
    extract_json_payload_records,
    extract_json_ld_events,
    extract_shinsegae_shopping_records,
    extract_skt_newsroom_seongsu_candidates,
    extract_xml_feed_records,
    expand_env_string,
    fetch_source,
    is_verifiable_record,
    normalize_record,
    normalized_body_hash,
    record_matches_source_filters,
    safe_http_url,
    source_schedule,
    validate_source_definition,
    validate_record,
)
from run_popup_trend_collector import (
    collect_once,
    merge_candidate_queue,
    merge_records,
    nonnegative_int,
    read_json_file,
    write_records_atomic,
)


class FakeResponse:
    def __init__(self, status_code=200, *, headers=None, text="", payload=None):
        self.status_code = status_code
        self.headers = headers or {}
        self.text = text
        self._payload = payload

    def json(self):
        if self._payload is None:
            raise ValueError("no JSON payload")
        return self._payload

    def raise_for_status(self):
        if self.status_code >= 400:
            import requests

            raise requests.HTTPError(f"HTTP {self.status_code}")


def paginated_seoul_source(*, page_size=2, max_pages=3):
    return {
        "id": "seoul-culture-seongsu-popups",
        "sourceType": "seoul_culture_api",
        "url": (
            "https://openapi.seoul.go.kr/key/json/culturalEventInfo/"
            "{{PAGE_START}}/{{PAGE_END}}/"
        ),
        "officialSourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
        "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr", "culture.seoul.go.kr"],
        "kind": "popup",
        "source": "SEOUL_CULTURE_API",
        "attribution": "서울특별시 문화행사 정보",
        "license": "공공누리 제1유형",
        "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
        "publicationPolicy": "allowed_with_attribution",
        "pagination": {"enabled": True, "pageSize": page_size, "maxPages": max_pages},
    }


def publication_rights():
    return {
        "attribution": "테스트 공식 제공자",
        "license": "테스트 게시 허가",
        "licenseUrl": "https://example.com/license",
        "publicationPolicy": "allowed_with_attribution",
    }


def seoul_popup_row(index=1, *, title=None):
    return {
        "TITLE": title or f"성수 {index}번 팝업",
        "CODENAME": "전시/미술",
        "PLACE": "성수 전시장",
        "STRTDATE": "2026-07-10",
        "END_DATE": "2026-07-20",
        "LAT": 37.5448,
        "LOT": 127.0504,
        "HMPG_ADDR": f"https://culture.seoul.go.kr/event/{index}",
    }


def skt_discovery_source():
    return {
        "id": "skt-newsroom-seongsu-discovery",
        "sourceType": "discovery",
        "url": "https://news.sktelecom.com/wp-json/wp/v2/posts?search=T%20Factory%20Seongsu",
        "officialSourceUrl": "https://news.sktelecom.com/",
        "allowedHosts": ["news.sktelecom.com"],
        "parser": "skt_newsroom_seongsu",
        "robotsUrl": "https://news.sktelecom.com/robots.txt",
        "usagePolicyUrl": "https://news.sktelecom.com/information-use",
        "publicationPolicy": "review_required",
        "persistRecords": False,
        "rightsStatus": "PROVIDER_PERMISSION_REQUIRED",
        "locationTerms": ["T 팩토리 성수", "T팩토리 성수", "서울시 성수동"],
        "topicTerms": ["팝업", "전시", "체험", "미니 포레스트"],
        "activeWindowDays": 120,
        "kind": "popup",
        "source": "SKT_NEWSROOM_DISCOVERY",
        "latitude": 37.54481912,
        "longitude": 127.05040398,
        "address": "서울특별시 성동구 연무장1길 7-1",
    }


def skt_newsroom_payload():
    return [
        {
            "id": 227286,
            "date": "2026-07-01T09:10:38",
            "modified": "2026-07-01T09:10:38",
            "link": "https://news.sktelecom.com/227286",
            "title": {"rendered": "SKT, T1과 함께 신규 전시 ‘암행천문: 별을 헤다’ 선보인다"},
            "excerpt": {"rendered": "<p>T 팩토리 성수 체험형 전시</p>"},
            "content": {"rendered": "<p>전시는 7월 2일부터 9월 13일까지 운영된다.</p>"},
        },
        {
            "id": 220000,
            "date": "2026-01-01T09:00:00",
            "modified": "2026-01-01T09:00:00",
            "link": "https://news.sktelecom.com/220000",
            "title": {"rendered": "지난 T 팩토리 성수 전시"},
            "excerpt": {"rendered": ""},
            "content": {"rendered": "<p>전시는 1월 2일부터 1월 10일까지 운영됐다.</p>"},
        },
        {
            "id": 227999,
            "date": "2026-07-01T09:00:00",
            "modified": "2026-07-01T09:00:00",
            "link": "https://news.sktelecom.com/227999",
            "title": {"rendered": "부산 체험 전시"},
            "excerpt": {"rendered": ""},
            "content": {"rendered": "<p>전시는 7월 2일부터 9월 13일까지 운영된다.</p>"},
        },
    ]


class CollectPopupTrendsTest(unittest.TestCase):
    def test_minimum_record_count_allows_honest_empty_output(self):
        self.assertEqual(nonnegative_int("0", 1), 0)
        self.assertEqual(nonnegative_int("-1", 0), 0)

    def test_extracts_only_popup_rows_from_shinsegae_official_embedded_payload(self):
        html = """
        <html><script>
          var g_shoppingInfo = {"page":[
            {"id":"8537003","storeCd":"SC00002","title1":"메디쏠라 팝업 스토어",
             "genreNm":"식품","badge1":"메디쏠라","startDt":"2026-07-09 21:00:00",
             "endDt":"2026-07-16 21:00:00","expDt":"26.07.10(금) - 26.07.16(목)",
             "imgUrl1":"/cms12/popup.jpg"},
            {"id":"8441841","storeCd":"SC00002","title1":"GOURMET ROAD TRIP",
             "genreNm":"해외패션","badge1":"팝업스토어","startDt":"2026-07-01 09:00:00",
             "endDt":"2026-07-12 21:50:00"},
            {"id":"ignored-store","storeCd":"SC00001","title1":"본점 팝업",
             "startDt":"2026-07-01","endDt":"2026-07-12"},
            {"id":"ignored-sale","storeCd":"SC00002","title1":"여름 정기 세일",
             "startDt":"2026-07-01","endDt":"2026-07-12"}
          ]};
        </script></html>
        """
        source = {
            "parser": "shinsegae_shopping",
            "storeCode": "SC00002",
            "officialSourceUrl": "https://www.shinsegae.com/shopping/list.do?storeCd=SC00002",
            "latitude": 37.503822,
            "longitude": 127.00453,
            "address": "서울특별시 서초구 신반포로 176 (반포동)",
        }

        records = extract_shinsegae_shopping_records(html, source)

        self.assertEqual(2, len(records))
        self.assertEqual("메디쏠라 팝업 스토어", records[0]["title"])
        self.assertEqual("2026-07-10", records[0]["startDate"])
        self.assertEqual("2026-07-16", records[0]["endDate"])
        self.assertEqual("8537003", records[0]["sourceContentId"])
        self.assertEqual("https://www.shinsegae.com/cms12/popup.jpg", records[0]["imageUrl"])
        self.assertEqual(source["officialSourceUrl"], records[0]["url"])

    def test_source_type_defaults_match_required_collection_cadence(self):
        self.assertEqual(86_400, DEFAULT_SOURCE_POLICIES["seoul_culture_api"]["intervalSeconds"])
        self.assertEqual(21_600, DEFAULT_SOURCE_POLICIES["tour_api"]["intervalSeconds"])
        self.assertEqual(14_400, DEFAULT_SOURCE_POLICIES["official_mall"]["intervalSeconds"])
        self.assertEqual(21_600, DEFAULT_SOURCE_POLICIES["rss"]["intervalSeconds"])
        self.assertEqual(43_200, DEFAULT_SOURCE_POLICIES["discovery"]["intervalSeconds"])

        first = source_schedule({"id": "lotte-popup", "sourceType": "official_mall"})
        second = source_schedule({"id": "shinsegae-popup", "sourceType": "official_mall"})

        self.assertEqual(14_400, first["intervalSeconds"])
        self.assertLessEqual(abs(first["jitterSeconds"]), 900)
        self.assertNotEqual(first["jitterSeconds"], second["jitterSeconds"])

    def test_network_source_requires_explicit_type_official_url_and_allowlist(self):
        valid_source = {
            "id": "seoul-culture",
            "sourceType": "seoul_culture_api",
            "url": "https://openapi.seoul.go.kr/example",
            "officialSourceUrl": "https://data.seoul.go.kr/example",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr"],
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": "https://data.seoul.go.kr/license",
            "publicationPolicy": "allowed_with_attribution",
        }

        self.assertIsNone(validate_source_definition(valid_source))
        self.assertIn("sourceType", validate_source_definition({**valid_source, "sourceType": None}))
        self.assertIn("officialSourceUrl", validate_source_definition({**valid_source, "officialSourceUrl": None}))
        self.assertIn(
            "allowlist",
            validate_source_definition({**valid_source, "url": "https://untrusted.example/events"}),
        )

    def test_persisted_network_source_requires_publication_rights_metadata(self):
        source = {
            "id": "partner-popup-feed",
            "sourceType": "partner_api",
            "url": "https://provider.example/events",
            "officialSourceUrl": "https://provider.example/policy",
            "allowedHosts": ["provider.example"],
        }

        self.assertIn("publicationPolicy", validate_source_definition(source))
        self.assertIn(
            "attribution",
            validate_source_definition({**source, "publicationPolicy": "allowed_with_attribution"}),
        )

    def test_discovery_source_requires_non_persistence_review_policy_and_policy_links(self):
        source = skt_discovery_source()

        self.assertIsNone(validate_source_definition(source))
        self.assertIn(
            "persistRecords",
            validate_source_definition({**source, "persistRecords": True}),
        )
        self.assertIn(
            "review_required",
            validate_source_definition({**source, "publicationPolicy": "allowed"}),
        )
        self.assertIn(
            "robotsUrl",
            validate_source_definition({**source, "robotsUrl": "https://tracker.example/robots.txt"}),
        )

    def test_source_policy_blocks_instagram_html_and_persisted_naver_search(self):
        instagram = {
            "id": "instagram-html",
            "sourceType": "official_mall",
            "url": "https://www.instagram.com/brand/",
            "officialSourceUrl": "https://www.instagram.com/brand/",
            "allowedHosts": ["instagram.com"],
        }
        naver_persisted = {
            "id": "naver-as-feed",
            "sourceType": "partner_api",
            "url": "https://openapi.naver.com/v1/search/blog.json?query=popup",
            "officialSourceUrl": "https://developers.naver.com/docs/serviceapi/search/blog/blog.md",
            "allowedHosts": ["openapi.naver.com", "developers.naver.com"],
        }

        self.assertIn("Instagram", validate_source_definition(instagram))
        self.assertIn("cannot be persisted", validate_source_definition(naver_persisted))

    def test_private_mock_host_requires_both_test_config_and_test_environment_gate(self):
        source = {
            **publication_rights(),
            "id": "local-mock",
            "sourceType": "seoul_culture_api",
            "url": "http://127.0.0.1:18081/events",
            "officialSourceUrl": "https://data.seoul.go.kr/events",
            "allowedHosts": ["127.0.0.1", "data.seoul.go.kr"],
            "testOnlyAllowPrivateHost": True,
        }

        with patch.dict("os.environ", {}, clear=False):
            import os

            os.environ.pop("NUGULMAP_ALLOW_PRIVATE_TEST_HOSTS", None)
            self.assertIn("absolute HTTP", validate_source_definition(source))
        with patch.dict("os.environ", {"NUGULMAP_ALLOW_PRIVATE_TEST_HOSTS": "true"}):
            self.assertIsNone(validate_source_definition(source))

    def test_normalized_body_hash_ignores_json_key_order_and_incidental_whitespace(self):
        first = normalized_body_hash('{"items": [{"title": "팝업", "id": 1}]}', "application/json")
        second = normalized_body_hash('{\n  "items": [ { "id": 1, "title": "팝업" } ]\n}', "application/json")

        self.assertEqual(first, second)

    def test_fetch_source_sends_conditional_headers_and_handles_304(self):
        source = {
            **publication_rights(),
            "id": "seoul-culture",
            "sourceType": "seoul_culture_api",
            "url": "https://openapi.seoul.go.kr/events",
            "officialSourceUrl": "https://data.seoul.go.kr/events",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr"],
        }
        request_get = Mock(return_value=FakeResponse(304, headers={"ETag": '"v2"'}))

        result = fetch_source(
            source,
            timeout=2,
            cached_state={
                "etag": '"v1"',
                "lastModified": "Fri, 10 Jul 2026 00:00:00 GMT",
                "recordCount": 1,
            },
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        headers = request_get.call_args.kwargs["headers"]
        self.assertEqual('"v1"', headers["If-None-Match"])
        self.assertEqual("Fri, 10 Jul 2026 00:00:00 GMT", headers["If-Modified-Since"])
        self.assertEqual("not_modified", result.status)
        self.assertEqual([], result.records)
        self.assertTrue(result.refresh_retained_records)
        self.assertFalse(request_get.call_args.kwargs["allow_redirects"])

    def test_fetch_source_uses_body_hash_when_server_has_no_cache_validators(self):
        source = {
            **publication_rights(),
            "id": "mall-popups",
            "sourceType": "official_mall",
            "url": "https://mall.example/events",
            "officialSourceUrl": "https://mall.example/events",
            "allowedHosts": ["mall.example"],
        }
        body = "<html> <body>same popup page</body> </html>"
        cached_hash = normalized_body_hash("<html><body>same popup page</body></html>", "text/html")

        result = fetch_source(
            source,
            timeout=2,
            cached_state={"bodyHash": cached_hash},
            request_get=Mock(return_value=FakeResponse(200, headers={"content-type": "text/html"}, text=body)),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("unchanged", result.status)
        self.assertEqual(cached_hash, result.state["bodyHash"])

    def test_collect_due_sources_does_not_request_source_before_next_run(self):
        source = {
            **publication_rights(),
            "id": "seoul-culture",
            "sourceType": "seoul_culture_api",
            "url": "https://openapi.seoul.go.kr/events",
            "officialSourceUrl": "https://data.seoul.go.kr/events",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr"],
        }
        state = {
            "sources": {
                "seoul-culture": {
                    "sourceId": "seoul-culture",
                    "sourceType": "seoul_culture_api",
                    "lastStatus": "success",
                    "nextRunAt": "2026-07-11T00:00:00+00:00",
                }
            }
        }
        request_get = Mock()

        batch = collect_due_sources(
            {"sources": [source]},
            timeout=2,
            state=state,
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        request_get.assert_not_called()
        self.assertEqual("not_due", batch.results[0].status)
        self.assertEqual("success", batch.state["sources"]["seoul-culture"]["lastStatus"])

    def test_fetch_source_retries_retry_after_then_exponential_backoff(self):
        source = {
            **publication_rights(),
            "id": "tour-events",
            "sourceType": "tour_api",
            "url": "https://apis.data.go.kr/events",
            "officialSourceUrl": "https://www.data.go.kr/data/events",
            "allowedHosts": ["apis.data.go.kr", "www.data.go.kr"],
            "maxAttempts": 3,
            "baseBackoffSeconds": 2,
        }
        payload = {
            "items": [
                {
                    "title": "서울 축제",
                    "startDate": "20260710",
                    "endDate": "20260711",
                    "mapy": 37.55,
                    "mapx": 126.98,
                    "address": "서울 중구",
                    "contentid": "event-1",
                    "link": "https://www.data.go.kr/data/events/1",
                }
            ]
        }
        request_get = Mock(
            side_effect=[
                FakeResponse(429, headers={"Retry-After": "3"}),
                FakeResponse(503),
                FakeResponse(200, headers={"content-type": "application/json"}, text="{}", payload=payload),
            ]
        )
        sleeps = []

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=sleeps.append,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual([3.0, 4.0], sleeps)
        self.assertEqual("success", result.status)
        self.assertEqual(1, len(result.records))

    def test_fetch_source_retries_transient_502_before_succeeding(self):
        source = {
            **publication_rights(),
            "id": "partner-feed",
            "sourceType": "partner_api",
            "url": "https://partner.example/api/events",
            "officialSourceUrl": "https://partner.example/events",
            "allowedHosts": ["partner.example"],
            "maxAttempts": 2,
            "baseBackoffSeconds": 1,
        }
        payload = {
            "items": [
                {
                    "title": "성수 파트너 팝업",
                    "startDate": "20260710",
                    "endDate": "20260711",
                    "latitude": 37.5448,
                    "longitude": 127.0504,
                    "address": "서울 성동구 성수동",
                    "link": "https://partner.example/events/1",
                }
            ]
        }
        request_get = Mock(
            side_effect=[
                FakeResponse(502),
                FakeResponse(200, headers={"content-type": "application/json"}, text=json.dumps(payload), payload=payload),
            ]
        )
        sleeps = []

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=sleeps.append,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual([1.0], sleeps)
        self.assertEqual("success", result.status)
        self.assertEqual(2, request_get.call_count)

    def test_fetch_source_never_follows_http_redirects(self):
        source = {
            **publication_rights(),
            "id": "partner-feed",
            "sourceType": "partner_api",
            "url": "https://partner.example/api/events",
            "officialSourceUrl": "https://partner.example/events",
            "allowedHosts": ["partner.example"],
        }
        request_get = Mock(return_value=FakeResponse(302, headers={"Location": "http://127.0.0.1/private"}))

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertFalse(request_get.call_args.kwargs["allow_redirects"])
        self.assertEqual("error", result.status)
        self.assertIn("redirect", result.state["lastError"])

    def test_fetch_source_disables_source_after_auth_rejection(self):
        source = {
            **publication_rights(),
            "id": "partner-feed",
            "sourceType": "partner_api",
            "url": "https://partner.example/api/events",
            "officialSourceUrl": "https://partner.example/events",
            "allowedHosts": ["partner.example"],
        }

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=Mock(return_value=FakeResponse(403)),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("disabled_auth", result.status)
        self.assertTrue(result.state["disabled"])
        self.assertEqual("http_403", result.state["disabledReason"])
        self.assertIsNone(result.state["nextRunAt"])

    def test_discovery_source_fetches_official_metadata_but_never_persists_it(self):
        source = skt_discovery_source()
        payload = skt_newsroom_payload()
        request_get = Mock(
            return_value=FakeResponse(
                200,
                headers={"content-type": "application/json"},
                text=json.dumps(payload, ensure_ascii=False),
                payload=payload,
            )
        )

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        request_get.assert_called_once()
        self.assertEqual("discovery_ready", result.status)
        self.assertFalse(result.persist_records)
        self.assertEqual([], result.records)
        self.assertTrue(result.replace_candidates)
        self.assertEqual(1, len(result.candidates))
        candidate = result.candidates[0]
        self.assertEqual("2026-07-02", candidate["startDate"])
        self.assertEqual("2026-09-13", candidate["endDate"])
        self.assertEqual("DISCOVERY_ONLY", candidate["collectionMode"])
        self.assertFalse(candidate["publishable"])
        self.assertFalse(is_verifiable_record(candidate))
        self.assertNotIn("content", candidate)
        self.assertNotIn("imageUrl", candidate)

    def test_korean_range_parser_and_skt_candidate_window_are_explicit(self):
        self.assertEqual(
            ("2026-12-30", "2027-01-03"),
            extract_korean_date_range("12월 30일부터 1월 3일까지", date(2026, 12, 1)),
        )

        candidates = extract_skt_newsroom_seongsu_candidates(
            skt_newsroom_payload(),
            skt_discovery_source(),
            datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual(["227286"], [candidate["sourceContentId"] for candidate in candidates])
        self.assertEqual(64, len(candidates[0]["candidateDigest"]))

    def test_seongsu_record_filter_requires_place_topic_and_coordinate_box(self):
        source = {
            "recordFilter": {
                "locationTerms": ["성수", "서울숲"],
                "titleTerms": ["팝업", "popup"],
                "topicTerms": ["팝업", "전시"],
                "bounds": {
                    "minLatitude": 37.532,
                    "maxLatitude": 37.558,
                    "minLongitude": 127.032,
                    "maxLongitude": 127.072,
                },
            }
        }
        matching = {
            "title": "성수 체험 팝업 전시",
            "kind": "전시",
            "address": "서울숲 인근",
            "latitude": 37.5448,
            "longitude": 127.0504,
        }

        self.assertTrue(record_matches_source_filters(matching, source))
        self.assertFalse(record_matches_source_filters({**matching, "title": "성수 체험 전시"}, source))
        self.assertFalse(record_matches_source_filters({**matching, "title": "성수 음악회", "kind": "공연"}, source))
        self.assertFalse(record_matches_source_filters({**matching, "latitude": 37.60}, source))

    def test_checked_in_seongsu_filter_rejects_seongdong_gu_only_eungbong_popup(self):
        config_path = Path(__file__).resolve().parents[1] / "config" / "popup_trend_sources.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        source = next(
            item
            for item in config["sources"]
            if item.get("id") == "seoul-culture-seongsu-popups"
        )
        eungbong_popup = {
            "title": "성동구 여름 팝업",
            "kind": "popup",
            "period": "2026-07-20-2026-07-30",
            "startDate": "2026-07-20",
            "endDate": "2026-07-30",
            "latitude": 37.5508,
            "longitude": 127.0335,
            "address": "서울 성동구 응봉동",
        }

        self.assertNotIn("성동구", source["recordFilter"]["locationTerms"])
        self.assertFalse(record_matches_source_filters(eungbong_popup, source))

    def test_seoul_record_uses_next_allowlisted_detail_link_candidate(self):
        source = paginated_seoul_source()
        raw = {
            **seoul_popup_row(),
            "ORG_LINK": "https://www.mfac.or.kr/event/123",
            "HMPG_ADDR": "https://culture.seoul.go.kr/culture/culture/cultureEvent/view.do?cultcode=123",
        }

        record = normalize_record(raw, source)

        self.assertIsNotNone(record)
        self.assertEqual(
            "https://culture.seoul.go.kr/culture/culture/cultureEvent/view.do?cultcode=123",
            record["detailUrl"],
        )

    def test_normalize_record_blocks_tobacco_nicotine_vape_commercial_metadata(self):
        source = {
            "id": "partner-popup-feed",
            "sourceType": "partner_api",
            "url": "https://api.example.com/events",
            "officialSourceUrl": "https://events.example.com",
            "allowedHosts": ["api.example.com", "events.example.com"],
            "kind": "popup",
        }
        safe_record = {
            "title": "성수 여름 체험 팝업",
            "address": "서울 성동구 성수동",
            "latitude": 37.5448,
            "longitude": 127.0504,
            "startDate": "2026-07-10",
            "endDate": "2026-07-20",
            "detailUrl": "https://events.example.com/summer-popup",
        }

        blocked_metadata = {
            "title": {"title": "성수 전자담배 신제품 팝업"},
            "place": {"PLACE": "성수 니코틴 쇼룸"},
            "description": {"description": "New vape brand launch and sampling"},
            "topic": {"topic": "Tobacco promotion"},
            "url": {"detailUrl": "https://events.example.com/vaping/launch"},
            "iqos_title": {"title": "성수 IQOS 신제품 팝업"},
            "iqos_korean_description": {"description": "아이코스 기기 체험 행사"},
            "juul_topic": {"topic": "JUUL product showcase"},
            "vaporizer_url": {"detailUrl": "https://events.example.com/vaporizer/launch"},
        }

        self.assertIsNotNone(normalize_record(safe_record, source))
        for field_name, metadata in blocked_metadata.items():
            with self.subTest(field=field_name):
                self.assertIsNone(normalize_record({**safe_record, **metadata}, source))

    def test_verifiable_record_rejects_tobacco_metadata_in_retained_output(self):
        record = {
            "id": "retained-vape-popup",
            "title": "성수 여름 팝업",
            "kind": "popup",
            "startDate": "2026-07-10",
            "endDate": "2026-07-20",
            "latitude": 37.5448,
            "longitude": 127.0504,
            "address": "서울 성동구 성수동",
            "source": "CRAWLED_POPUP_TREND",
            "sourceId": "partner-popup-feed",
            "sourceUrl": "https://events.example.com",
            "detailUrl": "https://events.example.com/nicotine-launch",
            "collectionMode": "NETWORK",
        }

        self.assertFalse(is_verifiable_record(record))

    def test_seoul_feed_publishes_only_filtered_seongsu_row_with_license_provenance(self):
        filters = {
            "locationTerms": ["성수", "서울숲"],
            "titleTerms": ["팝업", "popup"],
            "topicTerms": ["팝업", "전시"],
            "bounds": {
                "minLatitude": 37.532,
                "maxLatitude": 37.558,
                "minLongitude": 127.032,
                "maxLongitude": 127.072,
            },
        }
        source = {
            **publication_rights(),
            "id": "seoul-culture-seongsu-popups",
            "sourceType": "seoul_culture_api",
            "url": "https://openapi.seoul.go.kr/culturalEventInfo",
            "officialSourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr", "culture.seoul.go.kr"],
            "kind": "popup",
            "source": "SEOUL_CULTURE_API",
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
            "publicationPolicy": "allowed_with_attribution",
            "recordFilter": filters,
        }
        matching = {
            "TITLE": "성수 체험 팝업 전시",
            "CODENAME": "전시/미술",
            "PLACE": "성수 전시장",
            "STRTDATE": "2026-07-10",
            "END_DATE": "2026-07-20",
            "LAT": 37.5448,
            "LOT": 127.0504,
            "HMPG_ADDR": "https://culture.seoul.go.kr/event/1",
        }
        wrong_topic = {**matching, "TITLE": "성수 클래식 음악회", "CODENAME": "공연", "HMPG_ADDR": "https://culture.seoul.go.kr/event/2"}
        wrong_bounds = {**matching, "TITLE": "성수 팝업 외곽", "LAT": 37.60, "HMPG_ADDR": "https://culture.seoul.go.kr/event/3"}
        payload = {"culturalEventInfo": {"row": [matching, wrong_topic, wrong_bounds]}}

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=Mock(
                return_value=FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(payload, ensure_ascii=False),
                    payload=payload,
                )
            ),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("success", result.status)
        self.assertEqual(["성수 체험 팝업 전시"], [record["title"] for record in result.records])
        self.assertEqual("popup", result.records[0]["kind"])
        self.assertEqual("서울특별시 문화행사 정보", result.records[0]["attribution"])
        self.assertEqual("공공누리 제1유형", result.records[0]["license"])

    def test_seoul_feed_pages_through_the_official_total_before_filtering(self):
        source = {
            **publication_rights(),
            "id": "seoul-culture-seongsu-popups",
            "sourceType": "seoul_culture_api",
            "url": (
                "https://openapi.seoul.go.kr/key/json/culturalEventInfo/"
                "{{PAGE_START}}/{{PAGE_END}}/"
            ),
            "officialSourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr", "culture.seoul.go.kr"],
            "kind": "popup",
            "source": "SEOUL_CULTURE_API",
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
            "publicationPolicy": "allowed_with_attribution",
            "pagination": {"enabled": True, "pageSize": 2, "maxPages": 3},
            "recordFilter": {
                "locationTerms": ["성수", "서울숲"],
                "titleTerms": ["팝업", "popup"],
                "bounds": {
                    "minLatitude": 37.532,
                    "maxLatitude": 37.558,
                    "minLongitude": 127.032,
                    "maxLongitude": 127.072,
                },
            },
        }

        def row(title, homepage):
            return {
                "TITLE": title,
                "CODENAME": "전시/미술",
                "PLACE": "성수 전시장",
                "STRTDATE": "2026-07-10",
                "END_DATE": "2026-07-20",
                "LAT": 37.5448,
                "LOT": 127.0504,
                "HMPG_ADDR": homepage,
            }

        page_one = {
            "culturalEventInfo": {
                "list_total_count": 3,
                "row": [
                    row("성수 첫 번째 팝업", "https://culture.seoul.go.kr/event/1"),
                    {**row("잠실 팝업", "https://culture.seoul.go.kr/event/2"), "PLACE": "잠실", "LAT": 37.51},
                ],
            }
        }
        page_two = {
            "culturalEventInfo": {
                "list_total_count": 3,
                "row": [row("서울숲 두 번째 팝업", "https://culture.seoul.go.kr/event/3")],
            }
        }
        request_get = Mock(
            side_effect=[
                FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(page_one, ensure_ascii=False),
                    payload=page_one,
                ),
                FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(page_two, ensure_ascii=False),
                    payload=page_two,
                ),
            ]
        )

        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        requested_urls = [call.args[0] for call in request_get.call_args_list]
        self.assertEqual(
            [
                "https://openapi.seoul.go.kr/key/json/culturalEventInfo/1/2/",
                "https://openapi.seoul.go.kr/key/json/culturalEventInfo/3/3/",
            ],
            requested_urls,
        )
        self.assertEqual("success", result.status)
        self.assertEqual(3, result.state["totalAvailable"])
        self.assertEqual(2, result.state["pageCount"])
        self.assertEqual(
            ["성수 첫 번째 팝업", "서울숲 두 번째 팝업"],
            [record["title"] for record in result.records],
        )

    def test_seoul_feed_refuses_partial_results_when_total_exceeds_page_guard(self):
        source = {
            **publication_rights(),
            "id": "seoul-culture-seongsu-popups",
            "sourceType": "seoul_culture_api",
            "url": (
                "https://openapi.seoul.go.kr/key/json/culturalEventInfo/"
                "{{PAGE_START}}/{{PAGE_END}}/"
            ),
            "officialSourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr"],
            "pagination": {"enabled": True, "pageSize": 1000, "maxPages": 10},
        }
        payload = {
            "culturalEventInfo": {
                "list_total_count": 19_407,
                "row": [],
            }
        }
        request_get = Mock(
            return_value=FakeResponse(
                200,
                headers={"content-type": "application/json"},
                text=json.dumps(payload),
                payload=payload,
            )
        )

        result = fetch_source(
            source,
            timeout=2,
            cached_state={"lastStatus": "success", "recordCount": 4},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        request_get.assert_called_once()
        self.assertEqual("error", result.status)
        self.assertEqual([], result.records)
        self.assertFalse(result.replace_records)
        self.assertIn("pagination limit", result.state["lastError"])

    def test_seoul_feed_discards_partial_results_when_a_later_page_fails(self):
        source = {
            **publication_rights(),
            "id": "seoul-culture-seongsu-popups",
            "sourceType": "seoul_culture_api",
            "url": (
                "https://openapi.seoul.go.kr/key/json/culturalEventInfo/"
                "{{PAGE_START}}/{{PAGE_END}}/"
            ),
            "officialSourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr", "culture.seoul.go.kr"],
            "pagination": {"enabled": True, "pageSize": 2, "maxPages": 3},
            "maxAttempts": 1,
        }
        first_page = {
            "culturalEventInfo": {
                "list_total_count": 3,
                "row": [{"TITLE": "page one"}, {"TITLE": "page two"}],
            }
        }
        request_get = Mock(
            side_effect=[
                FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(first_page),
                    payload=first_page,
                ),
                FakeResponse(500),
            ]
        )

        result = fetch_source(
            source,
            timeout=2,
            cached_state={"lastStatus": "success", "recordCount": 4},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("backoff", result.status)
        self.assertEqual([], result.records)
        self.assertFalse(result.replace_records)
        self.assertEqual(2, request_get.call_count)

    def test_paginated_source_ignores_cached_conditionals_and_rejects_304(self):
        request_get = Mock(return_value=FakeResponse(304, headers={"ETag": '"page-one"'}))

        result = fetch_source(
            paginated_seoul_source(),
            timeout=2,
            cached_state={
                "etag": '"page-one"',
                "lastModified": "Fri, 10 Jul 2026 00:00:00 GMT",
            },
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        headers = request_get.call_args.kwargs["headers"]
        self.assertNotIn("If-None-Match", headers)
        self.assertNotIn("If-Modified-Since", headers)
        self.assertFalse(request_get.call_args.kwargs["allow_redirects"])
        self.assertEqual("error", result.status)
        self.assertFalse(result.replace_records)
        self.assertIn("unexpected HTTP 304", result.state["lastError"])

    def test_seoul_feed_rejects_http_200_error_envelope_on_later_page(self):
        first_page = {
            "culturalEventInfo": {
                "list_total_count": 3,
                "row": [seoul_popup_row(1), seoul_popup_row(2)],
            }
        }
        provider_error = {"RESULT": {"CODE": "ERROR-500", "MESSAGE": "provider error"}}
        request_get = Mock(
            side_effect=[
                FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(first_page),
                    payload=first_page,
                ),
                FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(provider_error),
                    payload=provider_error,
                ),
            ]
        )

        result = fetch_source(
            paginated_seoul_source(),
            timeout=2,
            cached_state={"lastStatus": "success", "recordCount": 4},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("error", result.status)
        self.assertEqual([], result.records)
        self.assertFalse(result.replace_records)
        self.assertIn("envelope", result.state["lastError"])

    def test_seoul_feed_rejects_total_count_drift_on_later_page(self):
        first_page = {
            "culturalEventInfo": {
                "list_total_count": 3,
                "row": [seoul_popup_row(1), seoul_popup_row(2)],
            }
        }
        drifted_page = {
            "culturalEventInfo": {
                "list_total_count": 4,
                "row": [seoul_popup_row(3)],
            }
        }
        request_get = Mock(
            side_effect=[
                FakeResponse(200, headers={"content-type": "application/json"}, text=json.dumps(first_page), payload=first_page),
                FakeResponse(200, headers={"content-type": "application/json"}, text=json.dumps(drifted_page), payload=drifted_page),
            ]
        )

        result = fetch_source(
            paginated_seoul_source(),
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("error", result.status)
        self.assertEqual([], result.records)
        self.assertIn("total count drift", result.state["lastError"])

    def test_seoul_feed_rejects_truncated_first_page(self):
        truncated_page = {
            "culturalEventInfo": {
                "list_total_count": 3,
                "row": [seoul_popup_row(1)],
            }
        }
        request_get = Mock(
            return_value=FakeResponse(
                200,
                headers={"content-type": "application/json"},
                text=json.dumps(truncated_page),
                payload=truncated_page,
            )
        )

        result = fetch_source(
            paginated_seoul_source(),
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        request_get.assert_called_once()
        self.assertEqual("error", result.status)
        self.assertEqual([], result.records)
        self.assertIn("row count mismatch on page 1", result.state["lastError"])

    def test_seoul_feed_rejects_truncated_later_page(self):
        first_page = {
            "culturalEventInfo": {
                "list_total_count": 4,
                "row": [seoul_popup_row(1), seoul_popup_row(2)],
            }
        }
        truncated_page = {
            "culturalEventInfo": {
                "list_total_count": 4,
                "row": [seoul_popup_row(3)],
            }
        }
        request_get = Mock(
            side_effect=[
                FakeResponse(200, headers={"content-type": "application/json"}, text=json.dumps(first_page), payload=first_page),
                FakeResponse(200, headers={"content-type": "application/json"}, text=json.dumps(truncated_page), payload=truncated_page),
            ]
        )

        result = fetch_source(
            paginated_seoul_source(),
            timeout=2,
            cached_state={},
            request_get=request_get,
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("error", result.status)
        self.assertEqual([], result.records)
        self.assertIn("row count mismatch on page 2", result.state["lastError"])

    def test_unchanged_full_feed_refreshes_retained_record_freshness(self):
        payload = {
            "culturalEventInfo": {
                "list_total_count": 1,
                "row": [seoul_popup_row(1)],
            }
        }

        def response():
            return FakeResponse(
                200,
                headers={"content-type": "application/json"},
                text=json.dumps(payload, ensure_ascii=False),
                payload=payload,
            )

        source = paginated_seoul_source()
        first = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=Mock(return_value=response()),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )
        first.records[0]["collectedAt"] = "2026-07-10T00:00:00+00:00"
        second = fetch_source(
            source,
            timeout=2,
            cached_state=first.state,
            request_get=Mock(return_value=response()),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 11, 0, 1, tzinfo=timezone.utc),
        )
        batch = CollectionBatch(
            records=second.records,
            results=[second],
            state={"sources": {source["id"]: second.state}},
        )

        merged = merge_records(first.records, batch, {"sources": [source]})

        self.assertEqual("unchanged", second.status)
        self.assertTrue(second.refresh_retained_records)
        self.assertEqual("2026-07-11T00:01:00+00:00", merged[0]["collectedAt"])

    def test_successful_empty_feed_preserves_last_good_without_refreshing_freshness(self):
        payload = {"culturalEventInfo": {"list_total_count": 0, "row": []}}
        source = paginated_seoul_source()
        existing = {
            "id": "last-good",
            "title": "성수 마지막 정상 팝업",
            "kind": "popup",
            "startDate": "2026-07-10",
            "endDate": "2026-07-20",
            "latitude": 37.5448,
            "longitude": 127.0504,
            "address": "성수 전시장",
            "detailUrl": "https://culture.seoul.go.kr/event/last-good",
            "sourceUrl": source["officialSourceUrl"],
            "sourceId": source["id"],
            "sourceType": "seoul_culture_api",
            "source": "SEOUL_CULTURE_API",
            "collectionMode": "NETWORK",
            "collectedAt": "2026-07-10T00:00:00+00:00",
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": source["licenseUrl"],
            "publicationPolicy": "allowed_with_attribution",
            "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
        }
        result = fetch_source(
            source,
            timeout=2,
            cached_state={},
            request_get=Mock(
                return_value=FakeResponse(
                    200,
                    headers={"content-type": "application/json"},
                    text=json.dumps(payload),
                    payload=payload,
                )
            ),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 11, tzinfo=timezone.utc),
        )
        batch = CollectionBatch(
            records=result.records,
            results=[result],
            state={"sources": {source["id"]: result.state}},
        )

        merged = merge_records([existing], batch, {"sources": [source]})

        self.assertEqual("empty", result.status)
        self.assertFalse(result.replace_records)
        self.assertFalse(result.refresh_retained_records)
        self.assertEqual("2026-07-10T00:00:00+00:00", merged[0]["collectedAt"])

    def test_seoul_payload_extractor_keeps_every_row_in_a_1000_row_page(self):
        rows = [
            {
                "TITLE": f"성수 팝업 {index}",
                "STRTDATE": "2026-07-10",
                "END_DATE": "2026-07-20",
                "PLACE": "성수동",
                "LAT": 37.5448,
                "LOT": 127.0504,
                "HMPG_ADDR": f"https://culture.seoul.go.kr/event/{index}",
            }
            for index in range(1_000)
        ]
        payload = {"culturalEventInfo": {"list_total_count": 1_000, "row": rows}}

        records = extract_json_payload_records(
            payload,
            {"sourceType": "seoul_culture_api", "source": "SEOUL_CULTURE_API"},
        )

        self.assertEqual(1_000, len(records))
        self.assertEqual("성수 팝업 0", records[0]["title"])
        self.assertEqual("성수 팝업 999", records[-1]["title"])

    def test_checked_in_config_has_seongsu_sources_and_no_manual_seeds(self):
        config_dir = Path(__file__).resolve().parents[1] / "config"
        for config_name in ("popup_trend_sources.json", "popup_trend_sources.example.json"):
            with self.subTest(config=config_name):
                config = json.loads((config_dir / config_name).read_text(encoding="utf-8"))
                sources = config["sources"]

                self.assertFalse(any(source.get("sourceType") == "manual" for source in sources))
                seoul_source = next(
                    source for source in sources if source.get("id") == "seoul-culture-seongsu-popups"
                )
                self.assertIn("{{PAGE_START}}/{{PAGE_END}}", seoul_source["url"])
                self.assertEqual("${SEOUL_CULTURE_API_PAGE_SIZE:-1000}", seoul_source["pagination"]["pageSize"])
                self.assertEqual("${SEOUL_CULTURE_API_MAX_PAGES:-25}", seoul_source["pagination"]["maxPages"])
                skt_discovery = next(
                    source for source in sources if source.get("id") == "skt-newsroom-seongsu-discovery"
                )
                self.assertFalse(skt_discovery.get("enabled"))
                self.assertIn("서면 허락", skt_discovery.get("disabledReason", ""))

    def test_collect_due_sources_skips_sample_and_manual_fallback_by_default(self):
        config = {
            "allowSampleFallback": False,
            "allowManualFallback": False,
            "sources": [
                {
                    **publication_rights(),
                    "id": "sample-seoul",
                    "sourceType": "seoul_culture_api",
                    "url": "https://openapi.seoul.go.kr/sample/json/events",
                    "officialSourceUrl": "https://data.seoul.go.kr/events",
                    "allowedHosts": ["openapi.seoul.go.kr", "data.seoul.go.kr"],
                },
                {
                    "id": "manual-candidate",
                    "sourceType": "manual",
                    "manual": True,
                    "title": "수동 샘플",
                    "latitude": 37.55,
                    "longitude": 126.98,
                },
            ],
        }

        batch = collect_due_sources(
            config,
            timeout=2,
            state={"sources": {}},
            request_get=Mock(),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual([], batch.records)
        self.assertEqual("disabled_sample", batch.results[0].status)
        self.assertEqual("disabled_manual_fallback", batch.results[1].status)

    def test_disabled_official_source_exposes_its_configured_health_reason(self):
        source = {
            "id": "mall-parser-pending",
            "sourceType": "official_mall",
            "enabled": False,
            "disabledReason": "no stable machine-readable dates and branch coordinates",
            "url": "https://mall.example/events",
            "officialSourceUrl": "https://mall.example/events",
            "allowedHosts": ["mall.example"],
        }

        batch = collect_due_sources(
            {"sources": [source]},
            timeout=2,
            state={"sources": {}},
            request_get=Mock(),
            sleep_fn=lambda _: None,
            now=datetime(2026, 7, 10, tzinfo=timezone.utc),
        )

        self.assertEqual("disabled_config", batch.results[0].status)
        self.assertEqual(source["disabledReason"], batch.results[0].message)
        self.assertEqual(source["disabledReason"], batch.results[0].state["disabledReason"])

    def test_network_record_contains_minimal_provenance_and_allowlisted_links(self):
        source = {
            "id": "official-mall",
            "sourceType": "official_mall",
            "source": "OFFICIAL_MALL",
            "officialSourceUrl": "https://mall.example/events",
            "allowedHosts": ["mall.example"],
            "url": "https://mall.example/events.json",
        }

        record = normalize_record(
            {
                "id": "event-1",
                "title": "공식 팝업",
                "startDate": "2026-07-10",
                "endDate": "2026-07-20",
                "latitude": 37.55,
                "longitude": 126.98,
                "url": "https://mall.example/events/1",
            },
            source,
        )
        rejected_link = normalize_record(
            {
                "id": "event-2",
                "title": "외부 링크 팝업",
                "latitude": 37.55,
                "longitude": 126.98,
                "url": "https://tracker.example/events/2",
            },
            source,
        )

        self.assertEqual("official-mall", record["sourceId"])
        self.assertEqual("official_mall", record["sourceType"])
        self.assertEqual("https://mall.example/events", record["sourceUrl"])
        self.assertEqual("https://mall.example/events/1", record["detailUrl"])
        self.assertIsNone(rejected_link["detailUrl"])

    def test_dataset_landing_page_cannot_replace_an_individual_popup_link(self):
        source = {
            "id": "official-feed",
            "sourceType": "partner_api",
            "source": "PARTNER_FEED",
            "officialSourceUrl": "https://provider.example/dataset",
            "allowedHosts": ["provider.example"],
            "url": "https://provider.example/api/events",
        }
        record = normalize_record(
            {
                "id": "event-without-detail",
                "title": "성수 개별 링크 없는 팝업",
                "startDate": "2026-07-10",
                "endDate": "2026-07-20",
                "latitude": 37.5446,
                "longitude": 127.0557,
                "address": "서울 성동구 연무장길",
            },
            source,
        )

        self.assertEqual("https://provider.example/dataset", record["sourceUrl"])
        self.assertIsNone(record["detailUrl"])
        self.assertFalse(is_verifiable_record(record))

    def test_verifiable_record_requires_real_dates_address_and_detail_link(self):
        valid = {
            "id": "strict-popup",
            "title": "성수 엄격 검증 팝업",
            "startDate": "2026-07-10",
            "endDate": "2026-07-20",
            "latitude": 37.5446,
            "longitude": 127.0557,
            "address": "서울 성동구 연무장길 1",
            "detailUrl": "https://example.com/popups/strict",
            "sourceUrl": "https://data.seoul.go.kr/dataset",
            "collectionMode": "NETWORK",
            "source": "SEOUL_CULTURE_API",
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": "https://example.com/license",
            "publicationPolicy": "allowed_with_attribution",
            "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
        }

        self.assertTrue(is_verifiable_record(valid))
        self.assertFalse(is_verifiable_record({key: value for key, value in valid.items() if key != "verificationStatus"}))
        self.assertFalse(is_verifiable_record({**valid, "startDate": "여름 예정"}))
        self.assertFalse(is_verifiable_record({**valid, "endDate": "2026-99-99"}))
        self.assertFalse(is_verifiable_record({**valid, "address": ""}))
        self.assertFalse(is_verifiable_record({**valid, "detailUrl": None}))

    def test_verifiable_record_rejects_reversed_and_expired_dates_in_seoul_time(self):
        valid = {
            "id": "date-contract-popup",
            "title": "성수 날짜 계약 팝업",
            "startDate": "2026-07-10",
            "endDate": "2026-07-11",
            "latitude": 37.5446,
            "longitude": 127.0557,
            "address": "서울 성동구 성수동",
            "detailUrl": "https://example.com/popups/date-contract",
            "sourceUrl": "https://data.seoul.go.kr/dataset",
            "collectionMode": "NETWORK",
            "source": "SEOUL_CULTURE_API",
            "attribution": "서울특별시 문화행사 정보",
            "license": "공공누리 제1유형",
            "licenseUrl": "https://example.com/license",
            "publicationPolicy": "allowed_with_attribution",
            "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
        }

        self.assertTrue(is_verifiable_record(valid, as_of=date(2026, 7, 11)))
        self.assertFalse(
            is_verifiable_record(
                {**valid, "startDate": "2026-07-12", "endDate": "2026-07-11"},
                as_of=date(2026, 7, 11),
            )
        )
        self.assertFalse(
            is_verifiable_record(
                {**valid, "startDate": "2026-07-01", "endDate": "2026-07-10"},
                as_of=date(2026, 7, 11),
            )
        )

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
        self.assertEqual("https://culture.seoul.go.kr/event", normalized["detailUrl"])
        self.assertNotEqual("https://culture.seoul.go.kr/event", normalized["sourceContentId"])

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
        self.assertEqual("https://example.com/jamsil-popup", record["detailUrl"])
        self.assertNotEqual("https://example.com/jamsil-popup", record["sourceContentId"])

    def test_separates_provider_identifier_from_safe_detail_url(self):
        record = normalize_record(
            {
                "id": "provider-123",
                "title": "성수 안전 링크 팝업",
                "latitude": 37.5446,
                "longitude": 127.0557,
                "url": "https://example.com/popup/123",
            },
            {"source": "PARTNER_FEED"},
        )

        self.assertEqual("provider-123", record["sourceContentId"])
        self.assertEqual("https://example.com/popup/123", record["detailUrl"])

    def test_rejects_non_http_detail_url(self):
        record = normalize_record(
            {
                "id": "provider-unsafe",
                "title": "잘못된 링크 팝업",
                "latitude": 37.5446,
                "longitude": 127.0557,
                "url": "javascript:alert(1)",
            },
            {"source": "PARTNER_FEED"},
        )

        self.assertIsNone(record["detailUrl"])
        self.assertIsNone(safe_http_url("data:text/html,unsafe"))
        self.assertIsNone(safe_http_url("http://127.0.0.1/private"))
        self.assertIsNone(safe_http_url("https://user:secret@example.com/private"))

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
        records = extract_xml_feed_records(
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
            {"url": "https://example.com/rss", "kind": "festival"},
        )

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
        self.assertEqual("https://example.com/a", records[0]["detailUrl"])
        self.assertNotEqual("https://example.com/a", records[0]["sourceContentId"])

    def test_write_records_atomic_replaces_existing_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "popup-trends.json"
            output.write_text("[]", encoding="utf-8")

            write_records_atomic(output, [{"id": "popup-test", "title": "테스트 팝업"}])

            self.assertIn("테스트 팝업", output.read_text(encoding="utf-8"))
            self.assertFalse(output.with_suffix(".json.tmp").exists())

    def test_collect_once_sanitizes_existing_file_when_only_manual_candidates_exist(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "sources.json"
            output = Path(temp_dir) / "popup-trends.json"
            config.write_text(
                '{"sources":[{"manual":true,"title":"수동 후보","latitude":37.5446,"longitude":127.0557}]}',
                encoding="utf-8",
            )
            output.write_text('[{"id":"previous-live","title":"기존 검증 데이터"}]', encoding="utf-8")

            exit_code = collect_once(config, output, timeout=1, min_records=1)

            self.assertEqual(2, exit_code)
            self.assertEqual([], read_json_file(output, None))
            self.assertNotIn("수동 후보", output.read_text(encoding="utf-8"))

    def test_collect_once_replaces_file_when_verified_network_record_exists(self):
        verified = {
            **publication_rights(),
            "id": "popup-network",
            "title": "검증된 팝업",
            "kind": "popup",
            "startDate": "2026-07-10",
            "endDate": "2026-07-20",
            "latitude": 37.5446,
            "longitude": 127.0557,
            "address": "서울 성동구 성수동",
            "detailUrl": "https://example.com/popup-network",
            "sourceUrl": "https://example.com/events",
            "source": "TEST_PARTNER_API",
            "collectionMode": "NETWORK",
            "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "sources.json"
            output = Path(temp_dir) / "popup-trends.json"
            config.write_text(json.dumps({"sources": [{
                **publication_rights(),
                "id": "test-source",
                "sourceType": "partner_api",
                "url": "https://api.example.com/events",
                "officialSourceUrl": "https://example.com/events",
                "allowedHosts": ["api.example.com", "example.com"],
            }]}), encoding="utf-8")
            output.write_text("[]", encoding="utf-8")

            source_state = {
                "sourceId": "test-source",
                "sourceType": "partner_api",
                "sourceUrl": "https://example.com",
                "lastStatus": "success",
                "recordCount": 1,
            }
            batch = CollectionBatch(
                records=[verified],
                results=[
                    SourceFetchResult(
                        "test-source",
                        "partner_api",
                        "success",
                        [verified],
                        source_state,
                        replace_records=True,
                    )
                ],
                state={"version": 1, "updatedAt": "2026-07-10T00:00:00+00:00", "sources": {"test-source": source_state}},
            )
            with patch("run_popup_trend_collector.collect_due_sources", return_value=batch):
                exit_code = collect_once(config, output, timeout=1, min_records=1)

            self.assertEqual(0, exit_code)
            self.assertIn("검증된 팝업", output.read_text(encoding="utf-8"))
            status = read_json_file(output.parent / "popup-source-status.json", {})
            state = read_json_file(output.parent / "popup-source-state.json", {})
            self.assertEqual("success", status["sources"][0]["status"])
            self.assertEqual("https://example.com", status["sources"][0]["sourceUrl"])
            self.assertNotIn("api.example.com", str(status))
            self.assertEqual("success", state["sources"]["test-source"]["lastStatus"])

    def test_collect_once_is_unhealthy_when_required_source_is_unavailable_without_fresh_records(self):
        source = {
            **publication_rights(),
            "id": "required-popup-source",
            "sourceType": "partner_api",
            "url": "https://api.example.com/events",
            "officialSourceUrl": "https://example.com/events",
            "allowedHosts": ["api.example.com", "example.com"],
            "requiredEnv": ["REQUIRED_POPUP_TEST_KEY"],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "sources.json"
            output = Path(temp_dir) / "popup-trends.json"
            status_path = Path(temp_dir) / "popup-source-status.json"
            config.write_text(json.dumps({"version": 1, "sources": [source]}), encoding="utf-8")
            output.write_text("[]", encoding="utf-8")

            with patch.dict(os.environ, {"REQUIRED_POPUP_TEST_KEY": ""}, clear=False):
                exit_code = collect_once(
                    config,
                    output,
                    timeout=1,
                    min_records=0,
                    status_path=status_path,
                    force=True,
                )

            status = read_json_file(status_path, {})
            self.assertEqual(2, exit_code)
            self.assertEqual("disabled_missing_env", status["sources"][0]["status"])
            self.assertEqual(0, status["summary"]["healthySources"])

    def test_collect_once_allows_successful_empty_required_source(self):
        source = {
            **publication_rights(),
            "id": "required-popup-source",
            "sourceType": "partner_api",
            "url": "https://api.example.com/events",
            "officialSourceUrl": "https://example.com/events",
            "allowedHosts": ["api.example.com", "example.com"],
            "requiredEnv": ["REQUIRED_POPUP_TEST_KEY"],
        }
        source_state = {
            "sourceUrl": source["officialSourceUrl"],
            "lastStatus": "empty",
            "lastSuccessAt": "2026-07-11T00:00:00+00:00",
            "recordCount": 0,
        }
        batch = CollectionBatch(
            records=[],
            results=[
                SourceFetchResult(
                    source["id"],
                    source["sourceType"],
                    "empty",
                    [],
                    source_state,
                )
            ],
            state={
                "version": 1,
                "updatedAt": "2026-07-11T00:00:00+00:00",
                "sources": {source["id"]: source_state},
            },
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "sources.json"
            output = Path(temp_dir) / "popup-trends.json"
            config.write_text(json.dumps({"version": 1, "sources": [source]}), encoding="utf-8")
            output.write_text("[]", encoding="utf-8")

            with patch("run_popup_trend_collector.collect_due_sources", return_value=batch):
                exit_code = collect_once(config, output, timeout=1, min_records=0)

            self.assertEqual(0, exit_code)
            self.assertEqual([], read_json_file(output, None))

    def test_collect_once_persists_removal_when_source_or_publication_rights_are_disabled(self):
        valid_source = paginated_seoul_source()
        retained = normalize_record(seoul_popup_row(), valid_source)
        self.assertIsNotNone(retained)
        retained["verificationStatus"] = "VERIFIED_SOURCE_RIGHTS"

        for status, configured_source in (
            ("disabled_config", {**valid_source, "enabled": False}),
            (
                "invalid_config",
                {
                    key: value
                    for key, value in valid_source.items()
                    if key not in {"publicationPolicy", "attribution", "license", "licenseUrl"}
                },
            ),
        ):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as temp_dir:
                config = Path(temp_dir) / "sources.json"
                output = Path(temp_dir) / "popup-trends.json"
                config.write_text(
                    json.dumps({"version": 1, "sources": [configured_source]}),
                    encoding="utf-8",
                )
                output.write_text(json.dumps([retained]), encoding="utf-8")
                source_state = {
                    "sourceUrl": valid_source["officialSourceUrl"],
                    "lastStatus": status,
                    "disabled": True,
                }
                batch = CollectionBatch(
                    records=[],
                    results=[
                        SourceFetchResult(
                            valid_source["id"],
                            valid_source["sourceType"],
                            status,
                            [],
                            source_state,
                        )
                    ],
                    state={
                        "version": 1,
                        "updatedAt": "2026-07-11T00:00:00+00:00",
                        "sources": {valid_source["id"]: source_state},
                    },
                )

                with patch("run_popup_trend_collector.collect_due_sources", return_value=batch):
                    exit_code = collect_once(config, output, timeout=1, min_records=0)

                self.assertEqual(0, exit_code)
                self.assertEqual([], read_json_file(output, None))

    def test_collect_once_does_not_commit_new_body_hash_when_output_write_fails(self):
        verified = {
            **publication_rights(),
            "id": "popup-network",
            "title": "검증된 새 팝업",
            "kind": "popup",
            "startDate": "2026-07-10",
            "endDate": "2026-07-20",
            "latitude": 37.5446,
            "longitude": 127.0557,
            "address": "서울 성동구 성수동",
            "detailUrl": "https://example.com/popup-network",
            "sourceUrl": "https://example.com/events",
            "sourceId": "test-source",
            "source": "TEST_PARTNER_API",
            "collectionMode": "NETWORK",
            "collectedAt": "2026-07-10T00:00:00+00:00",
            "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
        }
        source_state = {
            "sourceId": "test-source",
            "sourceType": "partner_api",
            "sourceUrl": "https://example.com/events",
            "lastStatus": "success",
            "lastSuccessAt": "2026-07-10T00:00:00+00:00",
            "bodyHash": "new-body-hash",
            "recordCount": 1,
        }
        batch = CollectionBatch(
            records=[verified],
            results=[
                SourceFetchResult(
                    "test-source",
                    "partner_api",
                    "success",
                    [verified],
                    source_state,
                    replace_records=True,
                )
            ],
            state={
                "version": 1,
                "updatedAt": "2026-07-10T00:00:00+00:00",
                "sources": {"test-source": source_state},
            },
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "sources.json"
            output = Path(temp_dir) / "popup-trends.json"
            state_path = Path(temp_dir) / "popup-source-state.json"
            config.write_text(json.dumps({"sources": [{
                **publication_rights(),
                "id": "test-source",
                "sourceType": "partner_api",
                "url": "https://api.example.com/events",
                "officialSourceUrl": "https://example.com/events",
                "allowedHosts": ["api.example.com", "example.com"],
            }]}), encoding="utf-8")
            output.write_text("[]", encoding="utf-8")
            state_path.write_text(
                '{"version":1,"sources":{"test-source":{"bodyHash":"old-body-hash"}}}',
                encoding="utf-8",
            )

            with (
                patch("run_popup_trend_collector.collect_due_sources", return_value=batch),
                patch("run_popup_trend_collector.write_records_atomic", side_effect=OSError("disk full")),
            ):
                with self.assertRaisesRegex(OSError, "disk full"):
                    collect_once(
                        config,
                        output,
                        timeout=1,
                        min_records=1,
                        state_path=state_path,
                    )

            persisted_state = read_json_file(state_path, {})
            self.assertEqual("old-body-hash", persisted_state["sources"]["test-source"]["bodyHash"])
            self.assertNotEqual("new-body-hash", str(persisted_state))

    def test_collect_once_writes_discovery_to_review_queue_only(self):
        candidate = extract_skt_newsroom_seongsu_candidates(
            skt_newsroom_payload(),
            skt_discovery_source(),
            datetime(2026, 7, 10, tzinfo=timezone.utc),
        )[0]
        source_state = {
            "sourceId": "skt-newsroom-seongsu-discovery",
            "sourceType": "discovery",
            "sourceUrl": "https://news.sktelecom.com/",
            "lastStatus": "discovery_ready",
            "recordCount": 0,
            "candidateCount": 1,
        }
        batch = CollectionBatch(
            records=[],
            results=[
                SourceFetchResult(
                    "skt-newsroom-seongsu-discovery",
                    "discovery",
                    "discovery_ready",
                    [],
                    source_state,
                    candidates=[candidate],
                    persist_records=False,
                    replace_candidates=True,
                )
            ],
            state={
                "version": 1,
                "updatedAt": "2026-07-10T00:00:00+00:00",
                "sources": {"skt-newsroom-seongsu-discovery": source_state},
            },
            candidates=[candidate],
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "sources.json"
            output = Path(temp_dir) / "popup-trends.json"
            review = Path(temp_dir) / "popup-verification-queue.json"
            config.write_text(json.dumps({"version": 1, "sources": [skt_discovery_source()]}), encoding="utf-8")
            output.write_text("[]", encoding="utf-8")

            with patch("run_popup_trend_collector.collect_due_sources", return_value=batch):
                exit_code = collect_once(
                    config,
                    output,
                    timeout=1,
                    min_records=0,
                    review_path=review,
                )

            self.assertEqual(0, exit_code)
            self.assertEqual([], read_json_file(output, None))
            review_document = read_json_file(review, {})
            self.assertFalse(review_document["publicationAllowed"])
            self.assertEqual([candidate["id"]], [item["id"] for item in review_document["candidates"]])

    def test_candidate_queue_replaces_only_successfully_refreshed_source(self):
        old_candidate = {
            "id": "old",
            "candidateDigest": "old-digest",
            "sourceId": "discovery-a",
            "collectionMode": "DISCOVERY_ONLY",
            "publishable": False,
        }
        new_candidate = {
            **old_candidate,
            "id": "new",
            "candidateDigest": "new-digest",
        }
        batch = CollectionBatch(
            records=[],
            results=[
                SourceFetchResult(
                    "discovery-a",
                    "discovery",
                    "discovery_ready",
                    [],
                    {},
                    candidates=[new_candidate],
                    persist_records=False,
                    replace_candidates=True,
                )
            ],
            state={"updatedAt": "2026-07-10T00:00:00+00:00", "sources": {}},
            candidates=[new_candidate],
        )

        merged = merge_candidate_queue({"candidates": [old_candidate]}, batch)

        self.assertEqual(["new"], [candidate["id"] for candidate in merged["candidates"]])

    def test_merge_drops_manual_legacy_and_unconfigured_existing_records(self):
        def verified(
            title,
            *,
            source_id=None,
            source_url=None,
            detail_url="https://official.example/events/1",
            mode="NETWORK",
        ):
            return {
                **publication_rights(),
                "id": title,
                "title": title,
                "startDate": "2026-07-10",
                "endDate": "2026-07-20",
                "latitude": 37.55,
                "longitude": 126.98,
                "address": "서울 성동구 성수동",
                "detailUrl": detail_url,
                "sourceUrl": source_url,
                "sourceId": source_id,
                "sourceType": "seoul_culture_api",
                "source": "TEST_APPROVED_SOURCE",
                "collectionMode": mode,
                "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
            }

        existing = [
            verified("수동 후보", mode="MANUAL"),
            verified("출처 없는 과거 API 행"),
            verified("미등록 출처", source_id="removed-source", source_url="https://removed.example/events"),
            verified(
                "공식 출처 불일치",
                source_id="seoul-culture-events",
                source_url="https://tracker.example/events",
            ),
            verified(
                "허용되지 않은 상세 링크",
                source_id="seoul-culture-events",
                source_url="https://data.seoul.go.kr/events",
                detail_url="https://tracker.example/events/1",
            ),
            verified(
                "현재 설정 출처",
                source_id="seoul-culture-events",
                source_url="https://data.seoul.go.kr/events",
                detail_url="https://culture.seoul.go.kr/events/1",
            ),
        ]
        fresh = verified(
            "신세계 공식 팝업",
            source_id="shinsegae-official-popups",
            source_url="https://www.shinsegae.com/shopping/list.do?storeCd=SC00002",
        )
        batch = CollectionBatch(
            records=[fresh],
            results=[
                SourceFetchResult(
                    "shinsegae-official-popups",
                    "official_mall",
                    "success",
                    [fresh],
                    {},
                    replace_records=True,
                ),
                SourceFetchResult("seoul-culture-events", "seoul_culture_api", "not_due", [], {}),
            ],
            state={
                "sources": {
                    "shinsegae-official-popups": {
                        "sourceUrl": "https://www.shinsegae.com/shopping/list.do?storeCd=SC00002"
                    },
                    "seoul-culture-events": {"sourceUrl": "https://data.seoul.go.kr/events"},
                }
            },
        )

        config = {
            "sources": [
                {
                    "id": "shinsegae-official-popups",
                    "officialSourceUrl": "https://www.shinsegae.com/shopping/list.do?storeCd=SC00002",
                    "allowedHosts": ["shinsegae.com"],
                },
                {
                    "id": "seoul-culture-events",
                    "sourceType": "seoul_culture_api",
                    "source": "TEST_APPROVED_SOURCE",
                    "url": "https://data.seoul.go.kr/events",
                    "officialSourceUrl": "https://data.seoul.go.kr/events",
                    "allowedHosts": ["data.seoul.go.kr", "culture.seoul.go.kr", "example.com"],
                    **publication_rights(),
                },
            ]
        }

        merged = merge_records(existing, batch, config)

        self.assertEqual(["신세계 공식 팝업", "현재 설정 출처"], [record["title"] for record in merged])

    def test_merge_reapplies_current_record_filter_to_not_due_retained_rows(self):
        source = paginated_seoul_source()
        source["recordFilter"] = {
            "locationTerms": ["성수", "서울숲", "연무장"],
            "titleTerms": ["팝업", "popup"],
            "topicTerms": ["팝업", "popup"],
            "bounds": {
                "minLatitude": 37.532,
                "maxLatitude": 37.558,
                "minLongitude": 127.032,
                "maxLongitude": 127.072,
            },
        }
        retained_eungbong = {
            "id": "retained-eungbong",
            "title": "성동구 여름 팝업",
            "kind": "popup",
            "period": "2026-07-20-2026-07-30",
            "startDate": "2026-07-20",
            "endDate": "2026-07-30",
            "latitude": 37.5508,
            "longitude": 127.0335,
            "address": "서울 성동구 응봉동",
            "detailUrl": "https://culture.seoul.go.kr/event/eungbong",
            "source": "SEOUL_CULTURE_API",
            "sourceId": source["id"],
            "sourceType": source["sourceType"],
            "sourceUrl": source["officialSourceUrl"],
            "sourceContentId": "eungbong",
            "collectionMode": "NETWORK",
            "collectedAt": "2026-07-10T00:00:00+00:00",
            "attribution": source["attribution"],
            "license": source["license"],
            "licenseUrl": source["licenseUrl"],
            "publicationPolicy": source["publicationPolicy"],
        }
        source_state = {
            "sourceUrl": source["officialSourceUrl"],
            "lastStatus": "not_due",
        }
        batch = CollectionBatch(
            records=[],
            results=[
                SourceFetchResult(
                    source["id"],
                    source["sourceType"],
                    "not_due",
                    [],
                    source_state,
                )
            ],
            state={
                "updatedAt": "2026-07-11T00:00:00+00:00",
                "sources": {source["id"]: source_state},
            },
        )

        merged = merge_records([retained_eungbong], batch, {"sources": [source]})

        self.assertEqual([], merged)

    def test_merge_drops_not_due_retained_row_when_current_source_identity_changes(self):
        source = {
            **publication_rights(),
            "id": "partner-popup-source",
            "sourceType": "partner_api",
            "source": "PARTNER_POPUP_API",
            "url": "https://api.example.com/events",
            "officialSourceUrl": "https://example.com/events",
            "allowedHosts": ["api.example.com", "example.com"],
        }
        retained = normalize_record(
            {
                "id": "partner-popup-1",
                "title": "성수 파트너 팝업",
                "startDate": "2026-07-10",
                "endDate": "2026-07-20",
                "latitude": 37.5446,
                "longitude": 127.0557,
                "address": "서울 성동구 성수동",
                "url": "https://example.com/events/1",
            },
            source,
        )
        self.assertIsNotNone(retained)
        source_state = {
            "sourceUrl": source["officialSourceUrl"],
            "lastStatus": "not_due",
        }

        for changed_source in (
            {**source, "source": "RENAMED_PARTNER_POPUP_API"},
            {**source, "sourceType": "official_mall"},
            {**source, "officialSourceUrl": "https://example.com/events-v2"},
        ):
            with self.subTest(changed_field=next(
                key for key in ("source", "sourceType", "officialSourceUrl")
                if changed_source[key] != source[key]
            )):
                batch = CollectionBatch(
                    records=[],
                    results=[
                        SourceFetchResult(
                            source["id"],
                            changed_source["sourceType"],
                            "not_due",
                            [],
                            source_state,
                        )
                    ],
                    state={
                        "updatedAt": "2026-07-11T00:00:00+00:00",
                        "sources": {source["id"]: source_state},
                    },
                )

                merged = merge_records([retained], batch, {"sources": [changed_source]})

                self.assertEqual([], merged)

    def test_merge_drops_verified_retained_rows_as_soon_as_source_is_disabled(self):
        source = paginated_seoul_source()
        source["enabled"] = False
        retained = normalize_record(seoul_popup_row(), paginated_seoul_source())
        self.assertIsNotNone(retained)
        retained["verificationStatus"] = "VERIFIED_SOURCE_RIGHTS"
        source_state = {
            "sourceUrl": source["officialSourceUrl"],
            "lastStatus": "disabled_config",
            "disabled": True,
        }
        batch = CollectionBatch(
            records=[],
            results=[
                SourceFetchResult(
                    source["id"],
                    source["sourceType"],
                    "disabled_config",
                    [],
                    source_state,
                )
            ],
            state={
                "updatedAt": "2026-07-11T00:00:00+00:00",
                "sources": {source["id"]: source_state},
            },
        )

        merged = merge_records([retained], batch, {"sources": [source]})

        self.assertEqual([], merged)

    def test_merge_drops_verified_retained_rows_when_publication_rights_are_removed(self):
        valid_source = paginated_seoul_source()
        source_without_rights = {
            key: value
            for key, value in valid_source.items()
            if key not in {"publicationPolicy", "attribution", "license", "licenseUrl"}
        }
        retained = normalize_record(seoul_popup_row(), valid_source)
        self.assertIsNotNone(retained)
        retained["verificationStatus"] = "VERIFIED_SOURCE_RIGHTS"
        source_state = {
            "sourceUrl": valid_source["officialSourceUrl"],
            "lastStatus": "invalid_config",
            "disabled": True,
        }
        batch = CollectionBatch(
            records=[],
            results=[
                SourceFetchResult(
                    valid_source["id"],
                    valid_source["sourceType"],
                    "invalid_config",
                    [],
                    source_state,
                )
            ],
            state={
                "updatedAt": "2026-07-11T00:00:00+00:00",
                "sources": {valid_source["id"]: source_state},
            },
        )

        merged = merge_records([retained], batch, {"sources": [source_without_rights]})

        self.assertEqual([], merged)


if __name__ == "__main__":
    unittest.main()
