package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.EventInsightResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EventInsightServiceTest {

    @Test
    void getEventsReturnsPopupFallbackWithoutExternalKey() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        EventInsightResponse response = service.getEvents("성수", 5);

        assertThat(response.dataFreshness()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().title()).contains("성수");
        assertThat(response.events().getFirst().kind()).isEqualTo("popup");
    }

    @Test
    void getEventsCapsFallbackResults() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        EventInsightResponse response = service.getEvents(null, 2);

        assertThat(response.events()).hasSize(2);
        assertThat(response.sources()).contains("한국관광공사 국문 관광정보 서비스");
    }

    @Test
    void getEventsFiltersFallbackByViewportBounds() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        EventInsightResponse response = service.getEvents(null, 10, 37.51, 37.54, 126.91, 126.95);

        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::title)
                .containsExactly("여의도 한강공원 행사·축제 후보");
    }

    @Test
    void getEventsReadsPopupTrendFile(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("popup-trends.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "popup-test-seongsu",
                    "title": "성수 테스트 팝업",
                    "kind": "popup",
                    "period": "이번 주",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "CRAWLED_POPUP_TREND",
                    "sourceContentId": "source-1"
                  }
                ]
                """);
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("테스트", 5);

        assertThat(response.dataFreshness()).isEqualTo("CRAWLED_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().title()).isEqualTo("성수 테스트 팝업");
        assertThat(response.events().getFirst().source()).isEqualTo("CRAWLED_POPUP_TREND");
    }

    @Test
    void getEventsReadsPopupTrendFileItemsObject(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("popup-trends-object.json");
        Files.writeString(trendFile, """
                {
                  "items": [
                    {
                      "id": "popup-object-seongsu",
                      "title": "성수 오브젝트 팝업",
                      "kind": "popup",
                      "period": "이번 주",
                      "latitude": 37.5446,
                      "longitude": 127.0557,
                      "address": "서울 성동구 성수동",
                      "source": "CRAWLED_POPUP_TREND",
                      "sourceContentId": "source-object"
                    }
                  ]
                }
                """);
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("오브젝트", 5);

        assertThat(response.dataFreshness()).isEqualTo("CRAWLED_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().id()).isEqualTo("popup-object-seongsu");
    }

    @Test
    void getEventsPreservesSeoulCultureApiSourceFromTrendFile(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("seoul-culture-events.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "seoul-culture-jamsil",
                    "title": "잠실 문화행사",
                    "kind": "축제",
                    "period": "2026-10-15~2026-10-15",
                    "startDate": "2026-10-15",
                    "endDate": "2026-10-15",
                    "latitude": 37.5125,
                    "longitude": 127.1028,
                    "address": "롯데월드타워",
                    "source": "SEOUL_CULTURE_API",
                    "sourceContentId": "https://culture.seoul.go.kr/event"
                  }
                ]
                """);
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("잠실", 5);

        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().source()).isEqualTo("SEOUL_CULTURE_API");
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("https://culture.seoul.go.kr/event");
    }

    @Test
    void getEventsFetchesSeoulCultureApiAtRuntime() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiEndIndex", 5);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("http://culture.example.test/base/culture-key/json/culturalEventInfo/1/5")))
                .andRespond(withSuccess("""
                        {
                          "culturalEventInfo": {
                            "row": [
                              {
                                "CODENAME": "전시",
                                "TITLE": "성수 런타임 문화행사",
                                "DATE": "2026-08-21~2026-08-23",
                                "PLACE": "서울 성동구 성수동",
                                "STRTDATE": "2026-08-21 00:00:00.0",
                                "END_DATE": "2026-08-23 00:00:00.0",
                                "LOT": "127.0557",
                                "LAT": "37.5446",
                                "MAIN_IMG": "https://example.com/culture.jpg",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/culture-event"
                              }
                            ]
                          }
                        }
                        """, new MediaType("application", "json", StandardCharsets.UTF_8)));

        EventInsightResponse response = service.getEvents("요즘 뜨는 장소 어디", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().title()).isEqualTo("성수 런타임 문화행사");
        assertThat(response.events().getFirst().kind()).isEqualTo("전시");
        assertThat(response.events().getFirst().startDate()).isEqualTo("2026-08-21");
        assertThat(response.events().getFirst().endDate()).isEqualTo("2026-08-23");
        assertThat(response.events().getFirst().source()).isEqualTo("SEOUL_CULTURE_API");
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("https://culture.seoul.go.kr/culture-event");
        assertThat(service.getSeoulCultureApiProviderStatus().qualityStatus()).isEqualTo("OK");
    }

    @Test
    void getEventsTreatsGenericTrendEventKeywordsAsDiscoveryIntent(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("generic-event-intent.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "seoul-culture-jamsil-concert",
                    "title": "잠실 콘서트",
                    "kind": "콘서트",
                    "period": "2026-10-15~2026-10-15",
                    "startDate": "2026-10-15 00:00:00.0",
                    "endDate": "2026-10-15 00:00:00.0",
                    "latitude": 37.5125,
                    "longitude": 127.1028,
                    "address": "롯데월드타워",
                    "source": "SEOUL_CULTURE_API"
                  },
                  {
                    "id": "seongsu-popup",
                    "title": "성수 브랜드 팝업",
                    "kind": "popup",
                    "period": "이번 주말",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "CRAWLED_POPUP_TREND"
                  }
                ]
                """);
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse discovery = service.getEvents("요즘 뜨는 팝업 행사 축제 어디", 5);
        EventInsightResponse seongsu = service.getEvents("성수 팝업", 5);

        assertThat(discovery.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(discovery.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("seoul-culture-jamsil-concert", "seongsu-popup");
        assertThat(seongsu.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("seongsu-popup");
    }

    @Test
    void getEventsTreatsDaySpecificOutingKeywordsAsDiscoveryIntent(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("outing-event-intent.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "today-exhibition",
                    "title": "오늘 가볼만한 전시회",
                    "kind": "전시",
                    "period": "오늘",
                    "startDate": "2026-10-15",
                    "endDate": "2026-10-15",
                    "latitude": 37.5759,
                    "longitude": 126.9768,
                    "address": "서울 종로구 세종대로",
                    "source": "CRAWLED_POPUP_TREND"
                  }
                ]
                """);
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("오늘 뭐하지", 5);

        assertThat(response.dataFreshness()).isEqualTo("CRAWLED_OR_PARTIAL");
        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("today-exhibition");
    }

    @Test
    void getEventsKeepsOnlyCurrentAndUpcomingCrawledEvents(@TempDir Path tempDir) throws Exception {
        LocalDate today = LocalDate.now();
        Path trendFile = tempDir.resolve("popup-trends-order.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "old-popup",
                    "title": "지난 팝업",
                    "kind": "popup",
                    "period": "지난달",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동"
                  },
                  {
                    "id": "upcoming-popup",
                    "title": "이번 주말 팝업",
                    "kind": "popup",
                    "period": "이번 주말",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동"
                  },
                  {
                    "id": "current-popup",
                    "title": "오늘 진행 중 팝업",
                    "kind": "popup",
                    "period": "오늘",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동"
                  }
                ]
                """.formatted(
                today.minusDays(14),
                today.minusDays(10),
                today.plusDays(2),
                today.plusDays(4),
                today.minusDays(1),
                today.plusDays(1)
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents(null, 3);

        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("current-popup", "upcoming-popup");
    }

    @Test
    void getEventsCachesTourApiResultsWithinTtl() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "test-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 180L);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("/B551011/KorService2/searchFestival2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "items": {
                                "item": [
                                  {
                                    "contentid": "tour-1",
                                    "title": "잠실 테스트 축제",
                                    "eventstartdate": "20260701",
                                    "eventenddate": "20260707",
                                    "mapy": "37.5125",
                                    "mapx": "127.1028",
                                    "addr1": "서울 송파구",
                                    "firstimage": "https://example.com/a.jpg"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """, new MediaType("application", "json", StandardCharsets.UTF_8)));

        EventInsightResponse first = service.getEvents("잠실", 5);
        EventInsightResponse second = service.getEvents("잠실", 5);

        server.verify();
        assertThat(first.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(second.events()).hasSize(1);
        assertThat(second.events().getFirst().title()).isEqualTo("잠실 테스트 축제");
        assertThat(second.events().getFirst().source()).isEqualTo("KTO_TOUR_API");
    }

    @Test
    void getEventsReadsTourApiItemsWhenItemsIsList() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "test-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("/B551011/KorService2/searchFestival2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "items": [
                                {
                                  "contentid": "tour-list-1",
                                  "title": "성수 리스트 축제",
                                  "eventstartdate": "20260701",
                                  "eventenddate": "20260703",
                                  "mapy": "37.5446",
                                  "mapx": "127.0557",
                                  "addr1": "서울 성동구"
                                }
                              ]
                            }
                          }
                        }
                        """, new MediaType("application", "json", StandardCharsets.UTF_8)));

        EventInsightResponse response = service.getEvents("성수", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().id()).isEqualTo("tour-list-1");
    }

    @Test
    void getEventsReadsTourApiBodyItemAndAlternativeFields() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "test-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("/B551011/KorService2/searchFestival2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "item": {
                                "contentId": "tour-alt-1",
                                "eventTitle": "잠실 대체 필드 행사",
                                "startDate": "2026.07.10",
                                "endDate": "2026.07.12",
                                "latitude": "37.5125",
                                "longitude": "127.1028",
                                "addr1": "서울 송파구",
                                "addr2": "올림픽로 240",
                                "firstimage2": "https://example.com/thumb.jpg"
                              }
                            }
                          }
                        }
                        """, new MediaType("application", "json", StandardCharsets.UTF_8)));

        EventInsightResponse response = service.getEvents("잠실", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().title()).isEqualTo("잠실 대체 필드 행사");
        assertThat(response.events().getFirst().startDate()).isEqualTo("2026.07.10");
        assertThat(response.events().getFirst().address()).isEqualTo("서울 송파구 올림픽로 240");
        assertThat(response.events().getFirst().imageUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("tour-alt-1");
    }

    @Test
    void getEventsFallsBackWhenTourApiReturnsEmptyItemsString() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "test-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("/B551011/KorService2/searchFestival2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "items": ""
                            }
                          }
                        }
                        """, new MediaType("application", "json", StandardCharsets.UTF_8)));

        EventInsightResponse response = service.getEvents("성수", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().source()).isEqualTo("STATIC_EVENT_SEED");
    }
}
