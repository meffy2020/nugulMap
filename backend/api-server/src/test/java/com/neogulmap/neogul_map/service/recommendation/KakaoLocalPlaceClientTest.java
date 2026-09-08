package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.dto.recommendation.CandidateFreshness;
import com.neogulmap.neogul_map.dto.recommendation.CandidateSearchResult;
import com.neogulmap.neogul_map.dto.recommendation.KakaoPlaceCategory;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.PlaceSearchArea;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoLocalPlaceClientTest {

    @Test
    void unconfiguredClientReturnsExplicitEmptyResultAndStillCapsPaging() {
        KakaoLocalPlaceClient client = new KakaoLocalPlaceClient(new RestTemplateBuilder());
        ReflectionTestUtils.setField(client, "restApiKey", " ");

        CandidateSearchResult result = client.searchCategory(
                KakaoPlaceCategory.CE7,
                new PlaceSearchArea.CenterRadius(37.55, 127.05, 1_000),
                500,
                500
        );

        assertThat(result.status()).isEqualTo(CandidateSearchResult.Status.UNCONFIGURED);
        assertThat(result.provider()).isEqualTo(PlaceProvider.KAKAO_LOCAL);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.page()).isEqualTo(KakaoLocalPlaceClient.MAX_PAGE);
        assertThat(result.size()).isEqualTo(KakaoLocalPlaceClient.MAX_SIZE);
    }

    @Test
    void serverKeyAloneDoesNotEnableProviderBeforePolicyGateIsOpened() {
        KakaoLocalPlaceClient client = new KakaoLocalPlaceClient(new RestTemplateBuilder());
        ReflectionTestUtils.setField(client, "restApiKey", "test-local-rest-key");
        ReflectionTestUtils.setField(client, "localRecommendationsEnabled", false);

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.searchCategory(
                KakaoPlaceCategory.CE7,
                new PlaceSearchArea.CenterRadius(37.55, 127.05, 1_000),
                1,
                15
        ).status()).isEqualTo(CandidateSearchResult.Status.UNCONFIGURED);
    }

    @Test
    void categorySearchUsesServerKeyCapsOfficialParametersAndMapsCandidateFields() {
        KakaoLocalPlaceClient client = configuredClient();
        MockRestServiceServer server = serverFor(client);
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/category.json");
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("KakaoAK test-local-rest-key");
                    Map<String, String> query = query(request.getURI().toString());
                    assertThat(query).containsEntry("category_group_code", "CE7")
                            .containsEntry("page", "45")
                            .containsEntry("size", "15")
                            .containsEntry("x", "127.05")
                            .containsEntry("y", "37.55")
                            .containsEntry("radius", "20000")
                            .containsEntry("sort", "distance");
                })
                .andRespond(withSuccess("""
                        {
                          "meta": {"is_end": false},
                          "documents": [{
                            "id": "123",
                            "place_name": "너굴 카페",
                            "category_name": "음식점 > 카페 > 커피전문점",
                            "category_group_name": "카페",
                            "address_name": "서울 성동구 성수동 1",
                            "road_address_name": "서울 성동구 연무장길 1",
                            "x": "127.051",
                            "y": "37.551",
                            "distance": "321",
                            "place_url": "http://place.map.kakao.com/123"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        CandidateSearchResult result = client.searchCategory(
                KakaoPlaceCategory.CE7,
                new PlaceSearchArea.CenterRadius(37.55, 127.05, 99_999),
                500,
                500
        );

        assertThat(result.status()).isEqualTo(CandidateSearchResult.Status.SUCCESS);
        assertThat(result.lastPage()).isFalse();
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo("123");
            assertThat(candidate.name()).isEqualTo("너굴 카페");
            assertThat(candidate.category()).isEqualTo("음식점 > 카페 > 커피전문점");
            assertThat(candidate.address()).isEqualTo("서울 성동구 연무장길 1");
            assertThat(candidate.latitude()).isEqualTo(37.551);
            assertThat(candidate.longitude()).isEqualTo(127.051);
            assertThat(candidate.distanceMeters()).isEqualTo(321);
            assertThat(candidate.placeUrl()).isEqualTo("http://place.map.kakao.com/123");
            assertThat(candidate.provider()).isEqualTo(PlaceProvider.KAKAO_LOCAL);
            assertThat(candidate.freshness()).isEqualTo(CandidateFreshness.LIVE);
            assertThat(candidate.tags()).contains("CE7", "카페", "커피전문점");
        });
        server.verify();
    }

    @Test
    void keywordSearchSupportsBoundsAndCalculatesDistanceWhenProviderOmitsIt() {
        KakaoLocalPlaceClient client = configuredClient();
        MockRestServiceServer server = serverFor(client);
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/keyword.json");
                    Map<String, String> query = query(request.getURI().toString());
                    assertThat(query).containsEntry("category_group_code", "CT1")
                            .containsEntry("query", "성수 데이트")
                            .containsEntry("rect", "127.0,37.5,127.1,37.6")
                            .doesNotContainKeys("radius", "sort");
                })
                .andRespond(withSuccess("""
                        {
                          "meta": {"is_end": true},
                          "documents": [{
                            "id": "culture-1",
                            "place_name": "성수 전시관",
                            "category_name": "문화,예술 > 문화시설 > 전시관",
                            "category_group_name": "문화시설",
                            "address_name": "서울 성동구 성수동",
                            "road_address_name": "",
                            "x": "127.05",
                            "y": "37.55",
                            "distance": "",
                            "place_url": "http://place.map.kakao.com/culture-1"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        CandidateSearchResult result = client.searchKeyword(
                " 성수 데이트 ",
                KakaoPlaceCategory.CT1,
                new PlaceSearchArea.Bounds(37.5, 127.0, 37.6, 127.1),
                1,
                10
        );

        assertThat(result.status()).isEqualTo(CandidateSearchResult.Status.SUCCESS);
        assertThat(result.lastPage()).isTrue();
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.address()).isEqualTo("서울 성동구 성수동");
            assertThat(candidate.distanceMeters()).isZero();
            assertThat(candidate.tags()).contains("CT1", "문화시설", "전시관");
        });
        server.verify();
    }

    @Test
    void upstreamFailureReturnsExplicitErrorWithoutCandidates() {
        KakaoLocalOutboundBulkhead bulkhead = new KakaoLocalOutboundBulkhead(1, 0);
        KakaoLocalPlaceClient client = configuredClient(bulkhead);
        MockRestServiceServer server = serverFor(client);
        server.expect(request -> assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("KakaoAK test-local-rest-key"))
                .andRespond(withServerError());

        CandidateSearchResult result = client.searchCategory(
                KakaoPlaceCategory.AT4,
                new PlaceSearchArea.CenterRadius(37.55, 127.05, 2_000),
                1,
                15
        );

        assertThat(result.status()).isEqualTo(CandidateSearchResult.Status.UPSTREAM_ERROR);
        assertThat(result.candidates()).isEmpty();
        server.verify();
        assertThat(bulkhead.tryAcquire()).isTrue();
        bulkhead.release();
    }

    @Test
    void saturatedBulkheadFailsFastWithoutCallingProvider() {
        KakaoLocalOutboundBulkhead bulkhead = new KakaoLocalOutboundBulkhead(1, 0);
        assertThat(bulkhead.tryAcquire()).isTrue();
        KakaoLocalPlaceClient client = new KakaoLocalPlaceClient(new RestTemplateBuilder(), bulkhead);
        ReflectionTestUtils.setField(client, "restApiKey", "test-local-rest-key");
        ReflectionTestUtils.setField(client, "localRecommendationsEnabled", true);
        MockRestServiceServer server = serverFor(client);

        CandidateSearchResult result = client.searchCategory(
                KakaoPlaceCategory.CE7,
                new PlaceSearchArea.CenterRadius(37.55, 127.05, 1_000),
                1,
                15
        );

        assertThat(result.status()).isEqualTo(CandidateSearchResult.Status.UPSTREAM_ERROR);
        assertThat(result.candidates()).isEmpty();
        server.verify();
        bulkhead.release();
    }

    @Test
    void bulkheadConfigurationHasSafeDefaultsAndHardBounds() throws Exception {
        KakaoLocalOutboundBulkhead upperBound = new KakaoLocalOutboundBulkhead(1_000, 10_000);
        KakaoLocalOutboundBulkhead lowerBound = new KakaoLocalOutboundBulkhead(0, -1);
        var applicationProperties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();
        String environmentTemplate = Files.readString(Path.of(".env.sample"));

        assertThat(upperBound.maxConcurrentRequests())
                .isEqualTo(KakaoLocalOutboundBulkhead.HARD_MAX_CONCURRENT_REQUESTS);
        assertThat(upperBound.acquireTimeoutMillis())
                .isEqualTo(KakaoLocalOutboundBulkhead.HARD_MAX_ACQUIRE_TIMEOUT_MILLIS);
        assertThat(lowerBound.maxConcurrentRequests()).isEqualTo(1);
        assertThat(lowerBound.acquireTimeoutMillis()).isZero();
        assertThat(applicationProperties.getProperty("external.kakao.bulkhead.max-concurrent-requests"))
                .isEqualTo("${KAKAO_LOCAL_MAX_CONCURRENT_REQUESTS:8}");
        assertThat(applicationProperties.getProperty("external.kakao.bulkhead.acquire-timeout-millis"))
                .isEqualTo("${KAKAO_LOCAL_BULKHEAD_ACQUIRE_TIMEOUT_MILLIS:50}");
        assertThat(applicationProperties.getProperty("external.kakao.local-recommendations-enabled"))
                .isEqualTo("${KAKAO_LOCAL_RECOMMENDATIONS_ENABLED:false}");
        assertThat(environmentTemplate).contains(
                "KAKAO_LOCAL_RECOMMENDATIONS_ENABLED=false",
                "KAKAO_LOCAL_MAX_CONCURRENT_REQUESTS=8",
                "KAKAO_LOCAL_BULKHEAD_ACQUIRE_TIMEOUT_MILLIS=50"
        );
    }

    @Test
    void invalidProviderDocumentsAreDiscardedWithoutBreakingValidResults() {
        KakaoLocalPlaceClient client = configuredClient();
        MockRestServiceServer server = serverFor(client);
        server.expect(request -> {
                })
                .andRespond(withSuccess("""
                        {
                          "meta": {"is_end": true},
                          "documents": [
                            {"id":"bad","place_name":"좌표 오류","x":"not-a-number","y":"37.55"},
                            {"id":"good","place_name":"정상 식당","category_name":"음식점 > 한식","x":"127.05","y":"37.55"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        CandidateSearchResult result = client.searchCategory(
                KakaoPlaceCategory.FD6,
                new PlaceSearchArea.CenterRadius(37.55, 127.05, 1_000),
                1,
                15
        );

        assertThat(result.candidates()).extracting(candidate -> candidate.id()).containsExactly("good");
        server.verify();
    }

    private static KakaoLocalPlaceClient configuredClient() {
        KakaoLocalPlaceClient client = new KakaoLocalPlaceClient(new RestTemplateBuilder());
        ReflectionTestUtils.setField(client, "restApiKey", "test-local-rest-key");
        ReflectionTestUtils.setField(client, "localRecommendationsEnabled", true);
        return client;
    }

    private static KakaoLocalPlaceClient configuredClient(KakaoLocalOutboundBulkhead bulkhead) {
        KakaoLocalPlaceClient client = new KakaoLocalPlaceClient(new RestTemplateBuilder(), bulkhead);
        ReflectionTestUtils.setField(client, "restApiKey", "test-local-rest-key");
        ReflectionTestUtils.setField(client, "localRecommendationsEnabled", true);
        return client;
    }

    private static MockRestServiceServer serverFor(KakaoLocalPlaceClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    private static Map<String, String> query(String uri) {
        return UriComponentsBuilder.fromUriString(uri)
                .build()
                .getQueryParams()
                .toSingleValueMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> java.net.URLDecoder.decode(entry.getValue(), UTF_8)
                ));
    }
}
