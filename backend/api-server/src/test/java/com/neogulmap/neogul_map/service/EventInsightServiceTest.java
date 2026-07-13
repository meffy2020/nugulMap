package com.neogulmap.neogul_map.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.EventInsightResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EventInsightServiceTest {

    @Test
    void getEventsDoesNotInventPopupFallbackWithoutExternalKey() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        EventInsightResponse response = service.getEvents("성수 팝업", 5);

        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(response.events()).isEmpty();
    }

    @Test
    void getEventsNeverCallsExternalProvidersOnTheRequestPath() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "configured-tour-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "configured-culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        EventInsightResponse response = service.getEvents("성수 팝업", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(response.events()).isEmpty();
    }

    @Test
    void getEventsCapsFallbackResults() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        EventInsightResponse response = service.getEvents(null, 2);

        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(response.events()).isEmpty();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void getEventsFiltersFallbackByViewportBounds() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        EventInsightResponse response = service.getEvents(null, 10, 37.51, 37.54, 126.91, 126.95);

        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(response.events()).isEmpty();
    }

    @Test
    void getEventsReadsPopupTrendFile(@TempDir Path tempDir) throws Exception {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(9).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("popup-trends.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "popup-test-seongsu",
                    "title": "성수 테스트 팝업",
                    "kind": "popup",
                    "period": "이번 주",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "sourceContentId": "source-1",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/source-1",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(startDate, endDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("테스트", 5);

        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().title()).isEqualTo("성수 테스트 팝업");
        assertThat(response.events().getFirst().source()).isEqualTo("SEOUL_CULTURE_API");
        assertThat(response.events().getFirst().attribution()).isEqualTo("서울특별시 문화행사 정보");
        assertThat(response.events().getFirst().sourceUrl())
                .isEqualTo("https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do");
        assertThat(response.events().getFirst().license()).isEqualTo("공공누리 제1유형");
        assertThat(response.events().getFirst().licenseUrl())
                .isEqualTo("https://www.kogl.or.kr/info/licenseType1.do");
    }

    @Test
    void getEventsRejectsObfuscatedPrivateAndLoopbackDetailUrls(@TempDir Path tempDir) throws Exception {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("popup-trends-obfuscated-private-links.json");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(trendFile.toFile(), List.of(
                approvedSeoulPopupRecord("decimal-loopback", "http://2130706433/private", eventDate, collectedAt),
                approvedSeoulPopupRecord("decimal-private", "http://167772161/private", eventDate, collectedAt),
                approvedSeoulPopupRecord("octal-loopback", "http://0177.0.0.1/private", eventDate, collectedAt),
                approvedSeoulPopupRecord("hex-loopback", "http://0x7f.0x0.0x0.0x1/private", eventDate, collectedAt),
                approvedSeoulPopupRecord("bracket-ipv6", "http://[::1]/private", eventDate, collectedAt),
                approvedSeoulPopupRecord("mapped-ipv6", "http://[::ffff:127.0.0.1]/private", eventDate, collectedAt)
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), objectMapper);
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("성수 팝업", 10);

        assertThat(response.events()).isEmpty();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    private Map<String, Object> approvedSeoulPopupRecord(
            String id,
            String detailUrl,
            String eventDate,
            String collectedAt
    ) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("title", "성수 주소 검증 팝업 " + id);
        record.put("kind", "popup");
        record.put("startDate", eventDate);
        record.put("endDate", eventDate);
        record.put("latitude", 37.5446);
        record.put("longitude", 127.0557);
        record.put("address", "서울 성동구 성수동");
        record.put("source", "SEOUL_CULTURE_API");
        record.put("sourceUrl", "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do");
        record.put("attribution", "서울특별시 문화행사 정보");
        record.put("license", "공공누리 제1유형");
        record.put("licenseUrl", "https://www.kogl.or.kr/info/licenseType1.do");
        record.put("publicationPolicy", "allowed_with_attribution");
        record.put("verificationStatus", "VERIFIED_SOURCE_RIGHTS");
        record.put("collectionMode", "NETWORK");
        record.put("detailUrl", detailUrl);
        record.put("collectedAt", collectedAt);
        return record;
    }

    @Test
    void getEventsPublishesOnlyPopupRowsWithApprovedSeoulSourceRights(@TempDir Path tempDir) throws Exception {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("popup-trends-source-rights.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "approved-seoul-popup",
                    "title": "성수 승인 출처 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://culture.seoul.go.kr/events/approved",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "unapproved-crawler-popup",
                    "title": "성수 임의 크롤링 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "CRAWLED_POPUP_TREND",
                    "sourceUrl": "https://example.com/events",
                    "attribution": "임의 제공자",
                    "license": "임의 허가",
                    "licenseUrl": "https://example.com/license",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/events/unapproved",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(
                eventDate, eventDate, collectedAt,
                eventDate, eventDate, collectedAt
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("성수", 5);

        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("approved-seoul-popup");
    }

    @Test
    void getEventsBlocksTobaccoNicotineAndVapePopupMetadata(@TempDir Path tempDir) throws Exception {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("popup-trends-tobacco-policy.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "safe-popup",
                    "title": "성수 여름 체험 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/safe",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "tobacco-title-popup",
                    "title": "성수 전자담배 신제품 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/title",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "nicotine-place-popup",
                    "title": "성수 장소 메타데이터 팝업",
                    "kind": "popup",
                    "place": "니코틴 쇼룸",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/place",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "vape-description-popup",
                    "title": "성수 설명 메타데이터 팝업",
                    "kind": "popup",
                    "description": "Vape brand launch and sampling",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/description",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "tobacco-topic-popup",
                    "title": "성수 주제 메타데이터 팝업",
                    "kind": "popup",
                    "topic": "Tobacco promotion",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/topic",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "vape-url-popup",
                    "title": "성수 링크 메타데이터 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/vape/launch",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(
                eventDate, eventDate, collectedAt,
                eventDate, eventDate, collectedAt,
                eventDate, eventDate, collectedAt,
                eventDate, eventDate, collectedAt,
                eventDate, eventDate, collectedAt,
                eventDate, eventDate, collectedAt
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents(null, 10);

        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("safe-popup");
    }

    @Test
    void getEventsBlocksIqosJuulAndVaporizerAliasesAcrossMetadata(@TempDir Path tempDir) throws Exception {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("popup-trends-tobacco-aliases.json");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(trendFile.toFile(), List.of(
                popupPolicyRecord("iqos-title", "title", "성수 IQOS 신제품 팝업", eventDate, collectedAt),
                popupPolicyRecord("iqos-korean-description", "description", "아이코스 기기 체험 행사", eventDate, collectedAt),
                popupPolicyRecord("juul-topic", "topic", "JUUL product showcase", eventDate, collectedAt),
                popupPolicyRecord("vaporizer-url", "detailUrl", "https://example.com/vaporizer/launch", eventDate, collectedAt)
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), objectMapper);
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents(null, 10);

        assertThat(response.events()).isEmpty();
    }

    private Map<String, Object> popupPolicyRecord(
            String id,
            String metadataField,
            String metadataValue,
            String eventDate,
            String collectedAt
    ) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("title", "성수 브랜드 체험 팝업");
        record.put("kind", "popup");
        record.put("startDate", eventDate);
        record.put("endDate", eventDate);
        record.put("latitude", 37.5446);
        record.put("longitude", 127.0557);
        record.put("address", "서울 성동구 성수동");
        record.put("source", "CRAWLED_POPUP_TREND");
        record.put("collectionMode", "NETWORK");
        record.put("detailUrl", "https://example.com/popups/" + id);
        record.put("collectedAt", collectedAt);
        record.put(metadataField, metadataValue);
        return record;
    }

    @Test
    void getEventsReadsPopupTrendFileItemsObject(@TempDir Path tempDir) throws Exception {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(9).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("popup-trends-object.json");
        Files.writeString(trendFile, """
                {
                  "items": [
                    {
                      "id": "popup-object-seongsu",
                      "title": "성수 오브젝트 팝업",
                      "kind": "popup",
                      "period": "이번 주",
                      "startDate": "%s",
                      "endDate": "%s",
                      "latitude": 37.5446,
                      "longitude": 127.0557,
                      "address": "서울 성동구 성수동",
                      "source": "SEOUL_CULTURE_API",
                      "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                      "attribution": "서울특별시 문화행사 정보",
                      "license": "공공누리 제1유형",
                      "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                      "publicationPolicy": "allowed_with_attribution",
                      "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                      "sourceContentId": "source-object",
                      "collectionMode": "NETWORK",
                      "detailUrl": "https://example.com/popups/source-object",
                      "collectedAt": "%s"
                    }
                  ]
                }
                """.formatted(startDate, endDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("오브젝트", 5);

        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().id()).isEqualTo("popup-object-seongsu");
    }

    @Test
    void getEventsRejectsVerifiedShapePopupOutsideSeongsu(@TempDir Path tempDir) throws Exception {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("non-seongsu-popup.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "gangnam-popup",
                    "title": "강남 브랜드 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.503822,
                    "longitude": 127.00453,
                    "address": "서울 서초구 신반포로",
                    "source": "SHINSEGAE_OFFICIAL",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/gangnam",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(startDate, endDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("성수 팝업", 5);

        assertThat(response.events()).isEmpty();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    @Test
    void getEventsRequiresVerifiedPublicLinksAndDateOrderForSeongsuPopupIntent(@TempDir Path tempDir) throws Exception {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String earlierEndDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(3).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("invalid-seongsu-popup-candidates.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "seongsu-festival",
                    "title": "성수 여름 축제",
                    "kind": "festival",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "KTO_TOUR_API",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/events/seongsu-festival",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "reversed-seongsu-popup",
                    "title": "성수 날짜 오류 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/events/reversed-popup",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "local-link-seongsu-popup",
                    "title": "성수 내부 링크 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "http://127.0.0.1/private",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(
                startDate, startDate, collectedAt,
                startDate, earlierEndDate, collectedAt,
                startDate, startDate, collectedAt
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("성수 팝업", 10);

        assertThat(response.events()).isEmpty();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    @Test
    void getEventsPreservesSeoulCultureApiSourceFromTrendFile(@TempDir Path tempDir) throws Exception {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("seoul-culture-events.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "seoul-culture-jamsil",
                    "title": "잠실 문화행사",
                    "kind": "축제",
                    "period": "%s~%s",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5125,
                    "longitude": 127.1028,
                    "address": "롯데월드타워",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "sourceContentId": "https://culture.seoul.go.kr/event",
                    "collectionMode": "NETWORK",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(eventDate, eventDate, eventDate, eventDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("잠실", 5);

        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().source()).isEqualTo("SEOUL_CULTURE_API");
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("seoul-culture-jamsil");
        assertThat(response.events().getFirst().detailUrl()).isEqualTo("https://culture.seoul.go.kr/event");
    }

    @Test
    void getEventsFetchesSeoulCultureApiAtRuntime() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 5);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("http://culture.example.test/base/culture-key/json/culturalEventInfo/1/5")))
                .andRespond(withSuccess("""
                        {
                          "culturalEventInfo": {
                            "list_total_count": 1,
                            "row": [
                              {
                                "CULTCODE": "158297",
                                "CODENAME": "전시",
                                "TITLE": "성수 런타임 팝업 문화행사",
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

        service.warmEventCache();
        EventInsightResponse response = service.getEvents("요즘 뜨는 장소 어디", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().title()).isEqualTo("성수 런타임 팝업 문화행사");
        assertThat(response.events().getFirst().kind()).isEqualTo("popup");
        assertThat(response.events().getFirst().startDate()).isEqualTo("2026-08-21");
        assertThat(response.events().getFirst().endDate()).isEqualTo("2026-08-23");
        assertThat(response.events().getFirst().source()).isEqualTo("SEOUL_CULTURE_API");
        assertThat(response.events().getFirst().attribution()).isEqualTo("서울특별시 문화행사 정보");
        assertThat(response.events().getFirst().sourceUrl())
                .isEqualTo("https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do");
        assertThat(response.events().getFirst().license()).isEqualTo("공공누리 제1유형");
        assertThat(response.events().getFirst().licenseUrl())
                .isEqualTo("https://www.kogl.or.kr/info/licenseType1.do");
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("158297");
        assertThat(response.events().getFirst().detailUrl()).isEqualTo("https://culture.seoul.go.kr/culture-event");
        assertThat(response.events().getFirst().collectedAt()).isNotBlank();
        assertThat(service.getSeoulCultureApiProviderStatus().qualityStatus()).isEqualTo("OK");
    }

    @Test
    void getEventsPublishesOnlyQualifiedSeongsuPopupRowsFromRuntimeSeoulCultureApi() {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(2).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(9).toString();
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 10);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("http://culture.example.test/base/culture-key/json/culturalEventInfo/1/10")))
                .andRespond(withSuccess("""
                        {
                          "culturalEventInfo": {
                            "list_total_count": 5,
                            "row": [
                              {
                                "CULTCODE": "valid-seongsu-popup",
                                "CODENAME": "전시",
                                "TITLE": "연무장 체험 팝업 전시",
                                "DATE": "%s~%s",
                                "PLACE": "서울 성동구 연무장길",
                                "STRTDATE": "%s 00:00:00.0",
                                "END_DATE": "%s 00:00:00.0",
                                "LOT": "127.0557",
                                "LAT": "37.5446",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/valid-seongsu-popup"
                              },
                              {
                                "CULTCODE": "wrong-location",
                                "CODENAME": "전시",
                                "TITLE": "강남 브랜드 팝업 전시",
                                "DATE": "%s~%s",
                                "PLACE": "서울 강남구",
                                "STRTDATE": "%s",
                                "END_DATE": "%s",
                                "LOT": "127.0557",
                                "LAT": "37.5446",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/wrong-location"
                              },
                              {
                                "CULTCODE": "wrong-topic",
                                "CODENAME": "공연",
                                "TITLE": "성수 클래식 음악회",
                                "DATE": "%s~%s",
                                "PLACE": "서울 성동구 성수동",
                                "STRTDATE": "%s",
                                "END_DATE": "%s",
                                "LOT": "127.0557",
                                "LAT": "37.5446",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/wrong-topic"
                              },
                              {
                                "CULTCODE": "wrong-bounds",
                                "CODENAME": "체험",
                                "TITLE": "서울숲 팝업 체험",
                                "DATE": "%s~%s",
                                "PLACE": "서울 성동구 서울숲",
                                "STRTDATE": "%s",
                                "END_DATE": "%s",
                                "LOT": "127.0557",
                                "LAT": "37.6000",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/wrong-bounds"
                              },
                              {
                                "CULTCODE": "eungbong-district-only",
                                "CODENAME": "전시",
                                "TITLE": "응봉 브랜드 팝업 전시",
                                "DATE": "%s~%s",
                                "PLACE": "서울 성동구 응봉동",
                                "STRTDATE": "%s",
                                "END_DATE": "%s",
                                "LOT": "127.0340",
                                "LAT": "37.5500",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/eungbong-district-only"
                              }
                            ]
                          }
                        }
                        """.formatted(
                        startDate, endDate, startDate, endDate,
                        startDate, endDate, startDate, endDate,
                        startDate, endDate, startDate, endDate,
                        startDate, endDate, startDate, endDate,
                        startDate, endDate, startDate, endDate
                ), new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
        EventInsightResponse response = service.getEvents("요즘 뜨는 장소 어디", 10);

        server.verify();
        assertThat(response.events()).hasSize(1);
        EventInsightResponse.EventInsightItem event = response.events().getFirst();
        assertThat(event.sourceContentId()).isEqualTo("valid-seongsu-popup");
        assertThat(event.kind()).isEqualTo("popup");
        assertThat(event.startDate()).isEqualTo(startDate);
        assertThat(event.endDate()).isEqualTo(endDate);
        assertThat(event.latitude()).isEqualTo(37.5446);
        assertThat(event.longitude()).isEqualTo(127.0557);
        assertThat(event.detailUrl()).isEqualTo("https://culture.seoul.go.kr/valid-seongsu-popup");
    }

    @Test
    void seoulCultureApiFetchesEveryPageFromListTotalCountBeforePublishing() {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 2);
        ReflectionTestUtils.setField(service, "seoulCultureApiMaxPages", 25);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("/culture-key/json/culturalEventInfo/1/2")))
                .andRespond(withSuccess(seoulCultureResponse(
                        3,
                        seoulCultureRow("page-1", "성수 페이지 1 팝업", eventDate),
                        seoulCultureRow("page-2", "성수 페이지 2 팝업", eventDate)
                ), new MediaType("application", "json", StandardCharsets.UTF_8)));
        server.expect(requestTo(containsString("/culture-key/json/culturalEventInfo/3/3")))
                .andRespond(withSuccess(seoulCultureResponse(
                        3,
                        seoulCultureRow("page-3", "성수 페이지 3 팝업", eventDate)
                ), new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
        EventInsightResponse response = service.getEvents("페이지", 10);

        server.verify();
        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::sourceContentId)
                .containsExactlyInAnyOrder("page-1", "page-2", "page-3");
        assertThat(service.getSeoulCultureApiProviderStatus().qualityStatus()).isEqualTo("OK");
    }

    @Test
    void seoulCultureApiBlocksTobaccoMetadataBeforeCaching() {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 1);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String blockedRow = seoulCultureRow("blocked-vape", "성수 브랜드 팝업", eventDate)
                .replace("\"HMPG_ADDR\"", "\"DESCRIPTION\": \"니코틴 vape 신제품 체험\",\n                  \"HMPG_ADDR\"");
        server.expect(requestTo(containsString("/culture-key/json/culturalEventInfo/1/1")))
                .andRespond(withSuccess(
                        seoulCultureResponse(1, blockedRow),
                        new MediaType("application", "json", StandardCharsets.UTF_8)
                ));

        service.warmEventCache();
        EventInsightResponse response = service.getEvents(null, 10);

        server.verify();
        assertThat(response.events()).isEmpty();
        assertThat(service.getSeoulCultureApiProviderStatus().qualityStatus()).isEqualTo("OK");
    }

    @Test
    void seoulCultureApiKeepsLastGoodCacheWhenALaterPageFails() {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 1);
        ReflectionTestUtils.setField(service, "seoulCultureApiMaxPages", 25);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 0L);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("/culture-key/json/culturalEventInfo/1/1")))
                .andRespond(withSuccess(seoulCultureResponse(
                        1,
                        seoulCultureRow("last-good", "성수 마지막 정상 캐시 팝업", eventDate)
                ), new MediaType("application", "json", StandardCharsets.UTF_8)));
        server.expect(requestTo(containsString("/culture-key/json/culturalEventInfo/1/1")))
                .andRespond(withSuccess(seoulCultureResponse(
                        2,
                        seoulCultureRow("partial-new", "성수 부분 결과 팝업", eventDate)
                ), new MediaType("application", "json", StandardCharsets.UTF_8)));
        server.expect(requestTo(containsString("/culture-key/json/culturalEventInfo/2/2")))
                .andRespond(withServerError());

        service.warmEventCache();
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 300L);
        service.warmEventCache();
        EventInsightResponse response = service.getEvents(null, 10);

        server.verify();
        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::sourceContentId)
                .containsExactly("last-good");
        assertThat(service.getSeoulCultureApiProviderStatus().qualityStatus()).isEqualTo("ERROR");
    }

    @Test
    void seoulCulturePaginationUsesSafeDefaultsAndHardBounds() {
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());

        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "normalizedSeoulCulturePageSize"))
                .isEqualTo(1000);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "normalizedSeoulCultureMaxPages"))
                .isEqualTo(25);
        assertThat((Long) ReflectionTestUtils.getField(service, "cacheTtlSeconds"))
                .isEqualTo(86_400L);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "requiredSeoulCulturePages", 19_407, 1000))
                .isEqualTo(20);

        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 5000);
        ReflectionTestUtils.setField(service, "seoulCultureApiMaxPages", 99);

        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "normalizedSeoulCulturePageSize"))
                .isEqualTo(1000);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "normalizedSeoulCultureMaxPages"))
                .isEqualTo(50);
    }

    @Test
    void providerFailuresDoNotExposeApiKeysInLogsOrStatusDetails() {
        String tourSecret = "tour-test-secret-key";
        String seoulSecret = "seoul-test-secret-key";
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", tourSecret);
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", seoulSecret);
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 5);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString(tourSecret))).andRespond(withServerError());
        server.expect(requestTo(containsString("/" + seoulSecret + "/json/culturalEventInfo/1/5")))
                .andRespond(withServerError());

        Logger logger = (Logger) LoggerFactory.getLogger(EventInsightService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.warmEventCache();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        server.verify();
        String logOutput = String.join("\n", appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList());
        String statusDetails = service.getTourApiProviderStatus().detail()
                + "\n"
                + service.getSeoulCultureApiProviderStatus().detail();
        assertThat(logOutput)
                .contains("KTO_TOUR_API", "SEOUL_CULTURE_API")
                .doesNotContain(tourSecret, seoulSecret);
        assertThat(statusDetails).doesNotContain(tourSecret, seoulSecret);
        assertThat(service.getTourApiProviderStatus().qualityStatus()).isEqualTo("ERROR");
        assertThat(service.getSeoulCultureApiProviderStatus().qualityStatus()).isEqualTo("ERROR");
    }

    @Test
    void getEventsDeduplicatesSameEventFromRuntimeApiAndCollector(@TempDir Path tempDir) throws Exception {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(10).toString();
        Path trendFile = tempDir.resolve("duplicate-seoul-culture-events.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "collector-copy",
                    "title": "성수 중복 팝업 행사",
                    "kind": "전시/미술",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "providerContentId": "158299",
                    "detailUrl": "https://culture.seoul.go.kr/duplicate-event",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(startDate, endDate, Instant.now()));

        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 5);
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("http://culture.example.test/base/culture-key/json/culturalEventInfo/1/5")))
                .andRespond(withSuccess("""
                        {
                          "culturalEventInfo": {
                            "list_total_count": 1,
                            "row": [
                              {
                                "CULTCODE": "158299",
                                "CODENAME": "전시",
                                "TITLE": "성수 중복 팝업 행사",
                                "DATE": "%s~%s",
                                "PLACE": "서울 성동구 성수동",
                                "STRTDATE": "%s 00:00:00.0",
                                "END_DATE": "%s 00:00:00.0",
                                "LOT": "127.0557",
                                "LAT": "37.5446",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/duplicate-event"
                              }
                            ]
                          }
                        }
                        """.formatted(startDate, endDate, startDate, endDate),
                        new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
        EventInsightResponse response = service.getEvents("중복", 5);

        server.verify();
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("158299");
        assertThat(response.events().getFirst().id()).startsWith("seoul-culture-");
    }

    @Test
    void getEventsPrefersVerifiedSeoulCulturePopupBeforeDeduplicatingProviderResults() {
        LocalDate start = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7);
        LocalDate end = start.plusDays(3);
        String startDate = start.toString();
        String endDate = end.toString();
        String tourStartDate = start.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String tourEndDate = end.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "tour-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiBaseUrl", "http://culture.example.test/base");
        ReflectionTestUtils.setField(service, "seoulCultureApiPageSize", 5);
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");
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
                                    "contentid": "tour-duplicate",
                                    "title": "성수 검증 우선 팝업",
                                    "eventstartdate": "%s",
                                    "eventenddate": "%s",
                                    "mapy": "37.5446",
                                    "mapx": "127.0557",
                                    "addr1": "서울 성동구 성수동",
                                    "homepage": "https://example.com/events/tour-duplicate"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """.formatted(tourStartDate, tourEndDate),
                        new MediaType("application", "json", StandardCharsets.UTF_8)));
        server.expect(requestTo(containsString("http://culture.example.test/base/culture-key/json/culturalEventInfo/1/5")))
                .andRespond(withSuccess("""
                        {
                          "culturalEventInfo": {
                            "list_total_count": 1,
                            "row": [
                              {
                                "CULTCODE": "verified-seoul-popup",
                                "CODENAME": "전시",
                                "TITLE": "성수 검증 우선 팝업",
                                "DATE": "%s~%s",
                                "PLACE": "서울 성동구 성수동",
                                "STRTDATE": "%s 00:00:00.0",
                                "END_DATE": "%s 00:00:00.0",
                                "LOT": "127.0557",
                                "LAT": "37.5446",
                                "HMPG_ADDR": "https://culture.seoul.go.kr/verified-seoul-popup"
                              }
                            ]
                          }
                        }
                        """.formatted(startDate, endDate, startDate, endDate),
                        new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
        EventInsightResponse response = service.getEvents("검증 우선", 5);

        server.verify();
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().source()).isEqualTo("SEOUL_CULTURE_API");
        assertThat(response.events().getFirst().sourceContentId()).isEqualTo("verified-seoul-popup");
        assertThat(response.events().getFirst().attribution()).isEqualTo("서울특별시 문화행사 정보");
        assertThat(response.events().getFirst().sourceUrl())
                .isEqualTo("https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do");
    }

    @Test
    void getEventsPromotesOnlySafeLegacyCrawlerUrls(@TempDir Path tempDir) throws Exception {
        String collectedAt = Instant.now().toString();
        String upcomingDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        Path trendFile = tempDir.resolve("popup-trends-safe-links.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "safe-popup",
                    "title": "안전 링크 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "sourceContentId": "https://example.com/popups/safe",
                    "collectionMode": "NETWORK",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "unsafe-popup",
                    "title": "위험 링크 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5450,
                    "longitude": 127.0560,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "sourceContentId": "javascript:alert(1)",
                    "collectionMode": "NETWORK",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(upcomingDate, upcomingDate, collectedAt, upcomingDate, upcomingDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("링크 팝업", 5);

        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("safe-popup");
        EventInsightResponse.EventInsightItem safe = response.events().getFirst();
        assertThat(safe.sourceContentId()).isEqualTo("safe-popup");
        assertThat(safe.detailUrl()).isEqualTo("https://example.com/popups/safe");
        assertThat(safe.collectedAt()).isEqualTo(collectedAt);
    }

    @Test
    void getEventsDoesNotPromoteSeoulDatasetLandingUrlToPopupDetailUrl(@TempDir Path tempDir) throws Exception {
        String collectedAt = Instant.now().toString();
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        Path trendFile = tempDir.resolve("popup-trends-seoul-dataset-link.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "seoul-dataset-only-popup",
                    "title": "성수 데이터셋 링크 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceContentId": "dataset-only-popup",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "collectionMode": "NETWORK",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(eventDate, eventDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("데이터셋", 5);

        assertThat(response.events()).isEmpty();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    private String seoulCultureResponse(int totalCount, String... rows) {
        return """
                {
                  "culturalEventInfo": {
                    "list_total_count": %d,
                    "row": [%s]
                  }
                }
                """.formatted(totalCount, String.join(",", rows));
    }

    private String seoulCultureRow(String id, String title, String eventDate) {
        return """
                {
                  "CULTCODE": "%s",
                  "CODENAME": "전시",
                  "TITLE": "%s",
                  "DATE": "%s~%s",
                  "PLACE": "서울 성동구 성수동 연무장길",
                  "STRTDATE": "%s 00:00:00.0",
                  "END_DATE": "%s 23:59:59.0",
                  "LOT": "127.0557",
                  "LAT": "37.5446",
                  "HMPG_ADDR": "https://culture.seoul.go.kr/events/%s"
                }
                """.formatted(id, title, eventDate, eventDate, eventDate, eventDate, id);
    }

    @Test
    void getEventsRejectsFreshNetworkRowsWithoutCompleteVerifiableMetadata(@TempDir Path tempDir) throws Exception {
        String collectedAt = Instant.now().toString();
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(9).toString();
        Path trendFile = tempDir.resolve("popup-trends-network-quality.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "valid-network-popup",
                    "title": "품질테스트 정상 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/valid",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "missing-start-popup",
                    "title": "품질테스트 시작일 없음",
                    "kind": "popup",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/missing-start",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "missing-end-popup",
                    "title": "품질테스트 종료일 없음",
                    "kind": "popup",
                    "startDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/missing-end",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "unsafe-url-popup",
                    "title": "품질테스트 위험 링크",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "javascript:alert(1)",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "missing-collected-at-popup",
                    "title": "품질테스트 수집시각 없음",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/missing-collected-at"
                  },
                  {
                    "id": "invalid-collected-at-popup",
                    "title": "품질테스트 잘못된 수집시각",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/invalid-collected-at",
                    "collectedAt": "not-an-instant"
                  }
                ]
                """.formatted(
                startDate, endDate, collectedAt,
                endDate, collectedAt,
                startDate, collectedAt,
                startDate, endDate, collectedAt,
                startDate, endDate,
                startDate, endDate
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("품질테스트", 10);
        EventInsightResponse rejectedOnlyResponse = service.getEvents("잘못된 수집시각", 10);

        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("valid-network-popup");
        assertThat(rejectedOnlyResponse.events()).isEmpty();
        assertThat(rejectedOnlyResponse.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    @Test
    void getEventsDoesNotTreatStaleCrawlerRecordsAsLive(@TempDir Path tempDir) throws Exception {
        String staleCollectedAt = Instant.now().minus(Duration.ofHours(25)).toString();
        String upcomingDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        Path trendFile = tempDir.resolve("popup-trends-stale.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "stale-popup",
                    "title": "오래된테스트 팝업",
                    "kind": "popup",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "CRAWLED_POPUP_TREND",
                    "sourceContentId": "stale-popup-source",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/stale",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(upcomingDate, upcomingDate, staleCollectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("오래된테스트", 5);

        assertThat(response.events()).isEmpty();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    @Test
    void getEventsRejectsManualSeedsFromPublicResults(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("popup-trends-manual.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "manual-popup",
                    "title": "수동테스트 팝업 후보",
                    "kind": "popup",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "MANUAL_SEED",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "manual-mode-popup",
                    "title": "수동테스트 모드 후보",
                    "kind": "popup",
                    "latitude": 37.5450,
                    "longitude": 127.0560,
                    "address": "서울 성동구 성수동",
                    "source": "CRAWLED_POPUP_TREND",
                    "collectionMode": "MANUAL",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(Instant.now(), Instant.now()));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("수동테스트", 5);

        assertThat(response.events()).isEmpty();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
    }

    @Test
    void getEventsTreatsGenericTrendEventKeywordsAsDiscoveryIntent(@TempDir Path tempDir) throws Exception {
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).toString();
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(9).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("generic-event-intent.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "seoul-culture-jamsil-concert",
                    "title": "잠실 콘서트",
                    "kind": "콘서트",
                    "period": "%s~%s",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5125,
                    "longitude": 127.1028,
                    "address": "롯데월드타워",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://culture.seoul.go.kr/events/jamsil-concert",
                    "collectedAt": "%s"
                  },
                  {
                    "id": "seongsu-popup",
                    "title": "성수 브랜드 팝업",
                    "kind": "popup",
                    "period": "이번 주말",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/seongsu",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(
                startDate, endDate, startDate, endDate, collectedAt,
                startDate, endDate, collectedAt
        ));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse discovery = service.getEvents("요즘 뜨는 팝업 행사 축제 어디", 5);
        EventInsightResponse seongsu = service.getEvents("성수 팝업", 5);

        assertThat(discovery.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(discovery.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactlyInAnyOrder("seoul-culture-jamsil-concert", "seongsu-popup");
        assertThat(seongsu.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("seongsu-popup");
    }

    @Test
    void getEventsTreatsDaySpecificOutingKeywordsAsDiscoveryIntent(@TempDir Path tempDir) throws Exception {
        String eventDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1).toString();
        String collectedAt = Instant.now().toString();
        Path trendFile = tempDir.resolve("outing-event-intent.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "today-exhibition",
                    "title": "오늘 가볼만한 전시회",
                    "kind": "전시",
                    "period": "오늘",
                    "startDate": "%s",
                    "endDate": "%s",
                    "latitude": 37.5759,
                    "longitude": 126.9768,
                    "address": "서울 종로구 세종대로",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/exhibitions/today",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(eventDate, eventDate, collectedAt));
        EventInsightService service = new EventInsightService(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        EventInsightResponse response = service.getEvents("오늘 뭐하지", 5);

        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.events())
                .extracting(EventInsightResponse.EventInsightItem::id)
                .containsExactly("today-exhibition");
    }

    @Test
    void getEventsKeepsOnlyCurrentAndUpcomingCrawledEvents(@TempDir Path tempDir) throws Exception {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        String collectedAt = Instant.now().toString();
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
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/old",
                    "collectedAt": "%s"
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
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/upcoming",
                    "collectedAt": "%s"
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
                    "address": "서울 성동구 성수동",
                    "source": "SEOUL_CULTURE_API",
                    "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                    "attribution": "서울특별시 문화행사 정보",
                    "license": "공공누리 제1유형",
                    "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do",
                    "publicationPolicy": "allowed_with_attribution",
                    "verificationStatus": "VERIFIED_SOURCE_RIGHTS",
                    "collectionMode": "NETWORK",
                    "detailUrl": "https://example.com/popups/current",
                    "collectedAt": "%s"
                  }
                ]
                """.formatted(
                today.minusDays(14),
                today.minusDays(10),
                collectedAt,
                today.plusDays(2),
                today.plusDays(4),
                collectedAt,
                today.minusDays(1),
                today.plusDays(1),
                collectedAt
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
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
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
                                    "eventstartdate": "%s",
                                    "eventenddate": "%s",
                                    "mapy": "37.5125",
                                    "mapx": "127.1028",
                                    "addr1": "서울 송파구",
                                    "firstimage": "https://example.com/a.jpg",
                                    "homepage": "https://example.com/events/tour-1"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """.formatted(startDate, endDate), new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
        service.warmEventCache();
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
        String startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String endDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(3)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
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
                                  "eventstartdate": "%s",
                                  "eventenddate": "%s",
                                  "mapy": "37.5446",
                                  "mapx": "127.0557",
                                  "addr1": "서울 성동구",
                                  "homepage": "https://example.com/events/tour-list-1"
                                }
                              ]
                            }
                          }
                        }
                        """.formatted(startDate, endDate), new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
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
                                "firstimage2": "https://example.com/thumb.jpg",
                                "homepage": "https://example.com/events/tour-alt-1"
                              }
                            }
                          }
                        }
                        """, new MediaType("application", "json", StandardCharsets.UTF_8)));

        service.warmEventCache();
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

        service.warmEventCache();
        EventInsightResponse response = service.getEvents("여의도", 5);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(response.events()).isEmpty();
        assertThat(response.sources()).isEmpty();
    }
}
