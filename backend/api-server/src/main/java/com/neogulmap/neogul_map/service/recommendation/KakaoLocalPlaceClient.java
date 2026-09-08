package com.neogulmap.neogul_map.service.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.neogulmap.neogul_map.dto.recommendation.CandidateFreshness;
import com.neogulmap.neogul_map.dto.recommendation.CandidateSearchResult;
import com.neogulmap.neogul_map.dto.recommendation.KakaoPlaceCategory;
import com.neogulmap.neogul_map.dto.recommendation.PlaceCandidate;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.PlaceSearchArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class KakaoLocalPlaceClient {

    public static final int MAX_PAGE = 45;
    public static final int MAX_SIZE = 15;
    public static final int MAX_RADIUS_METERS = 20_000;

    private static final String KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String CATEGORY_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final PlaceProvider PROVIDER = PlaceProvider.KAKAO_LOCAL;
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final RestTemplate restTemplate;
    private final KakaoLocalOutboundBulkhead outboundBulkhead;

    @Value("${external.kakao.local-rest-api-key:${KAKAO_LOCAL_REST_API_KEY:}}")
    private String restApiKey = "";

    @Value("${external.kakao.local-recommendations-enabled:${KAKAO_LOCAL_RECOMMENDATIONS_ENABLED:false}}")
    private boolean localRecommendationsEnabled;

    @Autowired
    public KakaoLocalPlaceClient(
            RestTemplateBuilder restTemplateBuilder,
            KakaoLocalOutboundBulkhead outboundBulkhead
    ) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        this.outboundBulkhead = Objects.requireNonNull(outboundBulkhead, "outboundBulkhead");
    }

    KakaoLocalPlaceClient(RestTemplateBuilder restTemplateBuilder) {
        this(
                restTemplateBuilder,
                new KakaoLocalOutboundBulkhead(
                        KakaoLocalOutboundBulkhead.DEFAULT_MAX_CONCURRENT_REQUESTS,
                        KakaoLocalOutboundBulkhead.DEFAULT_ACQUIRE_TIMEOUT_MILLIS
                )
        );
    }

    public CandidateSearchResult searchKeyword(
            String keyword,
            KakaoPlaceCategory category,
            PlaceSearchArea area,
            int page,
            int size
    ) {
        String normalizedKeyword = requireKeyword(keyword);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(area, "area");
        int normalizedPage = boundedPage(page);
        int normalizedSize = boundedSize(size);
        if (!isConfigured()) {
            return CandidateSearchResult.unconfigured(PROVIDER, normalizedPage, normalizedSize);
        }

        URI uri = buildUri(KEYWORD_URL, normalizedKeyword, category, area, normalizedPage, normalizedSize);
        return execute(uri, category, area, normalizedPage, normalizedSize);
    }

    public CandidateSearchResult searchCategory(
            KakaoPlaceCategory category,
            PlaceSearchArea area,
            int page,
            int size
    ) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(area, "area");
        int normalizedPage = boundedPage(page);
        int normalizedSize = boundedSize(size);
        if (!isConfigured()) {
            return CandidateSearchResult.unconfigured(PROVIDER, normalizedPage, normalizedSize);
        }

        URI uri = buildUri(CATEGORY_URL, null, category, area, normalizedPage, normalizedSize);
        return execute(uri, category, area, normalizedPage, normalizedSize);
    }

    public boolean isConfigured() {
        return localRecommendationsEnabled && restApiKey != null && !restApiKey.isBlank();
    }

    private CandidateSearchResult execute(
            URI uri,
            KakaoPlaceCategory category,
            PlaceSearchArea area,
            int page,
            int size
    ) {
        if (!outboundBulkhead.tryAcquire()) {
            return CandidateSearchResult.upstreamError(PROVIDER, page, size);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey.trim());
            ResponseEntity<KakaoSearchResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    KakaoSearchResponse.class
            );
            KakaoSearchResponse body = response.getBody();
            if (body == null) {
                return CandidateSearchResult.upstreamError(PROVIDER, page, size);
            }

            List<PlaceCandidate> candidates = mapDocuments(body.documents(), category, area);
            boolean lastPage = body.meta() == null || body.meta().isEnd();
            return CandidateSearchResult.success(PROVIDER, candidates, page, size, lastPage);
        } catch (RestClientException exception) {
            return CandidateSearchResult.upstreamError(PROVIDER, page, size);
        } finally {
            outboundBulkhead.release();
        }
    }

    private URI buildUri(
            String endpoint,
            String keyword,
            KakaoPlaceCategory category,
            PlaceSearchArea area,
            int page,
            int size
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("category_group_code", category.code())
                .queryParam("page", page)
                .queryParam("size", size);
        if (keyword != null) {
            builder.queryParam("query", keyword);
        }
        appendArea(builder, area);
        return builder.build().encode().toUri();
    }

    private void appendArea(UriComponentsBuilder builder, PlaceSearchArea area) {
        if (area instanceof PlaceSearchArea.CenterRadius center) {
            builder.queryParam("x", center.longitude())
                    .queryParam("y", center.latitude())
                    .queryParam("radius", Math.min(center.radiusMeters(), MAX_RADIUS_METERS))
                    .queryParam("sort", "distance");
            return;
        }

        PlaceSearchArea.Bounds bounds = (PlaceSearchArea.Bounds) area;
        String rect = bounds.minLongitude()
                + "," + bounds.minLatitude()
                + "," + bounds.maxLongitude()
                + "," + bounds.maxLatitude();
        builder.queryParam("rect", rect);
    }

    private List<PlaceCandidate> mapDocuments(
            List<KakaoDocument> documents,
            KakaoPlaceCategory requestedCategory,
            PlaceSearchArea area
    ) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<PlaceCandidate> candidates = new ArrayList<>();
        for (KakaoDocument document : documents) {
            PlaceCandidate candidate = mapDocument(document, requestedCategory, area);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private PlaceCandidate mapDocument(
            KakaoDocument document,
            KakaoPlaceCategory requestedCategory,
            PlaceSearchArea area
    ) {
        if (document == null || isBlank(document.id()) || isBlank(document.placeName())) {
            return null;
        }

        Double longitude = parseCoordinate(document.longitude(), -180, 180);
        Double latitude = parseCoordinate(document.latitude(), -90, 90);
        if (longitude == null || latitude == null) {
            return null;
        }

        String category = firstNonBlank(document.categoryName(), requestedCategory.tag());
        String address = firstNonBlank(document.roadAddressName(), document.addressName());
        Integer distanceMeters = parseDistance(document.distance());
        if (distanceMeters == null) {
            distanceMeters = calculateDistanceMeters(area, latitude, longitude);
        }

        return new PlaceCandidate(
                document.id(),
                document.placeName(),
                category,
                address,
                latitude,
                longitude,
                distanceMeters,
                document.placeUrl(),
                PROVIDER,
                CandidateFreshness.LIVE,
                tagsFor(document, requestedCategory)
        );
    }

    private Set<String> tagsFor(KakaoDocument document, KakaoPlaceCategory requestedCategory) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add(requestedCategory.code());
        tags.add(requestedCategory.tag());
        addIfPresent(tags, document.categoryGroupName());
        addIfPresent(tags, document.categoryName());
        if (!isBlank(document.categoryName())) {
            for (String part : document.categoryName().split(">")) {
                addIfPresent(tags, part);
            }
        }
        return tags;
    }

    private Integer calculateDistanceMeters(PlaceSearchArea area, double latitude, double longitude) {
        double anchorLatitude;
        double anchorLongitude;
        if (area instanceof PlaceSearchArea.CenterRadius center) {
            anchorLatitude = center.latitude();
            anchorLongitude = center.longitude();
        } else {
            PlaceSearchArea.Bounds bounds = (PlaceSearchArea.Bounds) area;
            anchorLatitude = (bounds.minLatitude() + bounds.maxLatitude()) / 2;
            anchorLongitude = (bounds.minLongitude() + bounds.maxLongitude()) / 2;
        }

        double latitudeDelta = Math.toRadians(latitude - anchorLatitude);
        double longitudeDelta = Math.toRadians(longitude - anchorLongitude);
        double startLatitude = Math.toRadians(anchorLatitude);
        double endLatitude = Math.toRadians(latitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double distance = 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
        return (int) Math.min(Integer.MAX_VALUE, Math.round(distance));
    }

    private static Double parseCoordinate(String rawValue, double minimum, double maximum) {
        if (isBlank(rawValue)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(rawValue.trim());
            return Double.isFinite(parsed) && parsed >= minimum && parsed <= maximum ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer parseDistance(String rawValue) {
        if (isBlank(rawValue)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int boundedPage(int page) {
        return Math.max(1, Math.min(MAX_PAGE, page));
    }

    private static int boundedSize(int size) {
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    private static String requireKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        return keyword.trim();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? (fallback == null ? "" : fallback.trim()) : preferred.trim();
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (!isBlank(value)) {
            target.add(value.trim());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoSearchResponse(KakaoMeta meta, List<KakaoDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoMeta(@JsonProperty("is_end") boolean isEnd) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoDocument(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_group_name") String categoryGroupName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("x") String longitude,
            @JsonProperty("y") String latitude,
            String distance,
            @JsonProperty("place_url") String placeUrl
    ) {
    }
}
