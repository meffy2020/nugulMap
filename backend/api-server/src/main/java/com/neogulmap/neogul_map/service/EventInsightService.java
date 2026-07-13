package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.EventInsightResponse;
import com.neogulmap.neogul_map.dto.EventInsightResponse.EventInsightItem;
import com.neogulmap.neogul_map.dto.InsightStatusResponse.ProviderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EventInsightService {

    private static final DateTimeFormatter TOUR_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DOTTED_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int SEOUL_CULTURE_MAX_PAGE_SIZE = 1000;
    private static final int SEOUL_CULTURE_HARD_MAX_PAGES = 50;
    private static final List<String> SEONGSU_LOCATION_TERMS = List.of(
            "성수",
            "서울숲",
            "연무장"
    );
    private static final List<String> SEONGSU_POPUP_INTENT_TERMS = List.of(
            "성수",
            "서울숲",
            "연무장",
            "성동구"
    );
    private static final List<String> EXPLICIT_POPUP_TERMS = List.of(
            "팝업",
            "popup"
    );
    private static final List<String> TOBACCO_NICOTINE_TERMS = List.of(
            "담배",
            "니코틴",
            "베이프",
            "궐련",
            "아이코스"
    );
    private static final Pattern TOBACCO_NICOTINE_ENGLISH_PATTERN = Pattern.compile(
            "(?<![a-z0-9])(?:tobacco|nicotine|iqos|juul|vap(?:e|es|ing)|vaporizer(?:s)?|vaporiser(?:s)?|cigarette(?:s)?|e[\\s_-]?cig(?:arette)?s?)(?![a-z0-9])",
            Pattern.CASE_INSENSITIVE
    );
    private static final String APPROVED_TREND_SOURCE = "SEOUL_CULTURE_API";
    private static final String APPROVED_TREND_ATTRIBUTION = "서울특별시 문화행사 정보";
    private static final String APPROVED_TREND_SOURCE_URL =
            "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do";
    private static final String APPROVED_TREND_LICENSE = "공공누리 제1유형";
    private static final String APPROVED_TREND_LICENSE_URL = "https://www.kogl.or.kr/info/licenseType1.do";
    private static final String APPROVED_PUBLICATION_POLICY = "allowed_with_attribution";
    private static final String VERIFIED_SOURCE_RIGHTS = "VERIFIED_SOURCE_RIGHTS";
    private static final List<String> TOBACCO_METADATA_FIELDS = List.of(
            "title",
            "name",
            "eventtitle",
            "displayname",
            "description",
            "summary",
            "excerpt",
            "content",
            "topic",
            "category",
            "kind",
            "codename",
            "period",
            "date",
            "address",
            "addr1",
            "addr2",
            "roadaddress",
            "locationname",
            "placename",
            "place",
            "guname",
            "detailurl",
            "sourceurl",
            "officialsourceurl",
            "orglink",
            "hmpgaddr",
            "eventhomepage",
            "url",
            "link",
            "href",
            "contenturl"
    );
    private static final List<String> GENERIC_EVENT_KEYWORDS = List.of(
            "요즘",
            "최근",
            "오늘",
            "내일",
            "이번 주말",
            "이번주",
            "이번 주",
            "이번달",
            "이번 달",
            "주말",
            "뜨는",
            "핫한",
            "핫플",
            "장소",
            "곳",
            "어디",
            "뭐하지",
            "갈만한",
            "가볼만한",
            "데이트",
            "나들이",
            "팝업",
            "popup",
            "행사",
            "event",
            "축제",
            "festival",
            "페스티벌",
            "공연",
            "전시",
            "전시회",
            "콘서트",
            "클래식",
            "플리마켓",
            "마켓"
    );
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedTourEvents> tourEventsCache = new ConcurrentHashMap<>();
    private final Map<String, CachedTourEvents> seoulCultureEventsCache = new ConcurrentHashMap<>();
    private final AtomicReference<ProviderAttempt> tourApiAttempt = new AtomicReference<>();
    private final AtomicReference<ProviderAttempt> seoulCultureApiAttempt = new AtomicReference<>();

    @Value("${external.kto.tour-api-key:${KTO_TOUR_API_KEY:}}")
    private String tourApiKey;

    @Value("${external.seoul.culture-api-key:${SEOUL_CULTURE_API_KEY:}}")
    private String seoulCultureApiKey;

    @Value("${external.seoul.culture-base-url:${SEOUL_CULTURE_API_BASE_URL:http://openapi.seoul.go.kr:8088}}")
    private String seoulCultureApiBaseUrl;

    @Value("${external.seoul.culture-api-page-size:${SEOUL_CULTURE_API_PAGE_SIZE:1000}}")
    private int seoulCultureApiPageSize = 1000;

    @Value("${external.seoul.culture-api-max-pages:${SEOUL_CULTURE_API_MAX_PAGES:25}}")
    private int seoulCultureApiMaxPages = 25;

    @Value("${external.seoul.culture-seongsu-min-latitude:${SEOUL_CULTURE_SEONGSU_MIN_LATITUDE:37.532}}")
    private double seongsuMinLatitude = 37.532;

    @Value("${external.seoul.culture-seongsu-max-latitude:${SEOUL_CULTURE_SEONGSU_MAX_LATITUDE:37.558}}")
    private double seongsuMaxLatitude = 37.558;

    @Value("${external.seoul.culture-seongsu-min-longitude:${SEOUL_CULTURE_SEONGSU_MIN_LONGITUDE:127.032}}")
    private double seongsuMinLongitude = 127.032;

    @Value("${external.seoul.culture-seongsu-max-longitude:${SEOUL_CULTURE_SEONGSU_MAX_LONGITUDE:127.072}}")
    private double seongsuMaxLongitude = 127.072;

    @Value("${external.popup-trends.file:${POPUP_TRENDS_FILE:}}")
    private String popupTrendsFile;

    @Value("${external.popup-trends.max-age-hours:${POPUP_TRENDS_MAX_AGE_HOURS:24}}")
    private long popupTrendsMaxAgeHours;

    @Value("${external.insights.event-cache-ttl-seconds:${INSIGHTS_EVENT_CACHE_TTL_SECONDS:86400}}")
    private long cacheTtlSeconds = 86_400;

    public EventInsightService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
    }

    public EventInsightResponse getEvents(String keyword, int limit) {
        return getEvents(keyword, limit, null, null, null, null);
    }

    public EventInsightResponse getEvents(
            String keyword,
            int limit,
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng
    ) {
        Bounds bounds = Bounds.of(minLat, maxLat, minLng, maxLng);
        List<EventInsightItem> liveEvents = new ArrayList<>();
        liveEvents.addAll(readFreshCachedEvents(tourEventsCache));
        liveEvents.addAll(readFreshCachedEvents(seoulCultureEventsCache));
        liveEvents.addAll(readPopupTrendEvents());

        List<EventInsightItem> publishableLiveEvents = liveEvents.stream()
                .filter(this::hasVerifiableEventMetadata)
                .filter(this::isCurrentOrUpcomingEvent)
                .filter(event -> !containsTobaccoOrNicotineTerms(event))
                .filter(event -> matches(event, keyword))
                .filter(event -> !isSeongsuPopupIntent(keyword) || isPublishableSeongsuPopup(event))
                .filter(event -> bounds.contains(event.latitude(), event.longitude()))
                .toList();

        List<EventInsightItem> normalizedLiveEvents = deduplicateEvents(
                prioritizeVerifiedPopupSources(publishableLiveEvents)
        ).stream()
                .sorted(eventPriorityComparator())
                .limit(normalizeLimit(limit))
                .toList();

        if (!normalizedLiveEvents.isEmpty()) {
            String freshness = normalizedLiveEvents.stream().anyMatch(this::isPublicEventApiSource)
                    ? "LIVE_OR_PARTIAL"
                    : "CRAWLED_OR_PARTIAL";
            return new EventInsightResponse(
                    normalizedLiveEvents,
                    freshness,
                    Instant.now(),
                    responseSources(normalizedLiveEvents)
            );
        }

        return new EventInsightResponse(List.of(), "NO_VERIFIED_DATA", Instant.now(), List.of());
    }

    /**
     * Refreshes provider snapshots outside the public request path. The scheduled
     * caller is the only normal runtime entry point that performs provider I/O.
     */
    public void warmEventCache() {
        fetchTourEvents(20);
        fetchSeoulCultureEvents();
    }

    public ProviderStatus getTourApiProviderStatus() {
        if (tourApiKey == null || tourApiKey.isBlank()) {
            return ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured");
        }

        ProviderAttempt attempt = tourApiAttempt.get();
        if (attempt == null) {
            return ProviderStatus.configuredUnverified("한국관광공사 TourAPI 키는 설정됐지만 이번 런타임에서 아직 조회 성공 여부를 확인하지 않았습니다.");
        }
        if (attempt.lastFailureAt() == null || (attempt.lastSuccessAt() != null && attempt.lastSuccessAt().isAfter(attempt.lastFailureAt()))) {
            return ProviderStatus.ok(attempt.lastSuccessAt(), attempt.detail());
        }
        return ProviderStatus.error(attempt.lastSuccessAt(), attempt.lastFailureAt(), attempt.detail());
    }

    public ProviderStatus getSeoulCultureApiProviderStatus() {
        if (seoulCultureApiKey == null || seoulCultureApiKey.isBlank()) {
            return ProviderStatus.notConfigured("SEOUL_CULTURE_API_KEY is not configured");
        }

        ProviderAttempt attempt = seoulCultureApiAttempt.get();
        if (attempt == null) {
            return ProviderStatus.configuredUnverified("서울 문화행사 API 키는 설정됐지만 이번 런타임에서 아직 조회 성공 여부를 확인하지 않았습니다.");
        }
        if (attempt.lastFailureAt() == null || (attempt.lastSuccessAt() != null && attempt.lastSuccessAt().isAfter(attempt.lastFailureAt()))) {
            return ProviderStatus.ok(attempt.lastSuccessAt(), attempt.detail());
        }
        return ProviderStatus.error(attempt.lastSuccessAt(), attempt.lastFailureAt(), attempt.detail());
    }

    private List<EventInsightItem> readPopupTrendEvents() {
        if (popupTrendsFile == null || popupTrendsFile.isBlank()) {
            return List.of();
        }

        File file = new File(popupTrendsFile.trim());
        if (!file.isFile()) {
            return List.of();
        }

        try {
            Object payload = objectMapper.readValue(file, Object.class);
            List<Map<String, Object>> records = readPopupTrendRecords(payload);
            return records.stream()
                    .filter(item -> !containsTobaccoOrNicotineTerms(item))
                    .map(this::toEligiblePopupTrendEvent)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception error) {
            log.warn("팝업 트렌드 파일 읽기 실패: {}", file.getAbsolutePath());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readPopupTrendRecords(Object payload) {
        Object items = payload;
        if (payload instanceof Map<?, ?> mapPayload) {
            items = mapPayload.get("items");
        }

        if (!(items instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .toList();
    }

    private List<EventInsightItem> fetchTourEvents(int limit) {
        if (tourApiKey == null || tourApiKey.isBlank()) {
            return List.of();
        }

        int normalizedLimit = normalizeLimit(limit);
        String cacheKey = TOUR_DATE.format(LocalDate.now(SEOUL_ZONE)) + ":" + normalizedLimit;
        Instant now = Instant.now();
        CachedTourEvents cached = tourEventsCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.events();
        }

        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://apis.data.go.kr/B551011/KorService2/searchFestival2")
                    .queryParam("serviceKey", tourApiKey.trim())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "NugulMap")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "O")
                    .queryParam("areaCode", "1")
                    .queryParam("eventStartDate", TOUR_DATE.format(LocalDate.now(SEOUL_ZONE)))
                    .queryParam("numOfRows", normalizedLimit)
                    .queryParam("pageNo", "1")
                    .build(false)
                    .toUriString();

            Object response = restTemplate.getForObject(url, Object.class);
            List<EventInsightItem> events = readTourItems(response).stream()
                    .filter(item -> !containsTobaccoOrNicotineTerms(item))
                    .map(this::toEvent)
                    .filter(Objects::nonNull)
                    .toList();
            tourEventsCache.put(cacheKey, new CachedTourEvents(events, Instant.now().plus(cacheDuration())));
            recordTourApiSuccess("한국관광공사 행사정보 조회 성공: " + events.size() + "건");
            return events;
        } catch (RuntimeException error) {
            logProviderFailure("KTO_TOUR_API", error);
            recordTourApiFailure("한국관광공사 행사정보 조회 실패");
            return List.of();
        }
    }

    private List<EventInsightItem> fetchSeoulCultureEvents() {
        if (seoulCultureApiKey == null || seoulCultureApiKey.isBlank()) {
            return List.of();
        }

        int pageSize = normalizedSeoulCulturePageSize();
        int maxPages = normalizedSeoulCultureMaxPages();
        // This endpoint is not date-scoped, so keep one stable key and retain the
        // last complete snapshot even across a KST date rollover.
        String cacheKey = pageSize + ":" + maxPages;
        Instant now = Instant.now();
        CachedTourEvents cached = seoulCultureEventsCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.events();
        }

        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            int totalCount = -1;
            int fetchedPages = 0;

            for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                int startIndex = Math.addExact(Math.multiplyExact(pageIndex, pageSize), 1);
                int endIndex = totalCount < 0
                        ? pageSize
                        : Math.min(totalCount, Math.addExact(startIndex, pageSize - 1));
                String url = UriComponentsBuilder
                        .fromUriString(normalizedSeoulCultureBaseUrl())
                        .pathSegment(
                                seoulCultureApiKey.trim(),
                                "json",
                                "culturalEventInfo",
                                String.valueOf(startIndex),
                                String.valueOf(endIndex)
                        )
                        .build(false)
                        .toUriString();

                Object response = restTemplate.getForObject(url, Object.class);
                Integer responseTotalCount = readSeoulCultureTotalCount(response);
                if (responseTotalCount == null) {
                    throw new IllegalStateException("서울 문화행사 API 응답에 list_total_count가 없습니다.");
                }
                if (totalCount < 0) {
                    totalCount = responseTotalCount;
                    int requiredPages = requiredSeoulCulturePages(totalCount, pageSize);
                    if (requiredPages > maxPages) {
                        throw new IllegalStateException("서울 문화행사 API 전체 결과가 설정된 페이지 한도를 초과합니다.");
                    }
                } else if (responseTotalCount != totalCount) {
                    throw new IllegalStateException("서울 문화행사 API 페이지별 list_total_count가 일치하지 않습니다.");
                }

                List<Map<String, Object>> pageRows = readSeoulCultureItems(response);
                int expectedRows = totalCount == 0
                        ? 0
                        : Math.max(0, Math.min(pageSize, totalCount - startIndex + 1));
                if (pageRows.size() != expectedRows) {
                    throw new IllegalStateException("서울 문화행사 API 페이지 결과가 일부 누락되었습니다.");
                }
                rows.addAll(pageRows);
                fetchedPages++;

                if (totalCount == 0 || endIndex >= totalCount) {
                    break;
                }
            }

            if (totalCount < 0 || rows.size() != totalCount) {
                throw new IllegalStateException("서울 문화행사 API 전체 페이지 수집이 완료되지 않았습니다.");
            }

            List<EventInsightItem> events = rows.stream()
                    .filter(item -> !containsTobaccoOrNicotineTerms(item))
                    .map(this::toSeoulCultureEvent)
                    .filter(Objects::nonNull)
                    .filter(this::isPublishableSeongsuPopup)
                    .toList();
            seoulCultureEventsCache.put(cacheKey, new CachedTourEvents(events, Instant.now().plus(cacheDuration())));
            recordSeoulCultureApiSuccess(
                    "서울 문화행사 API 전체 조회 성공: " + totalCount + "건, " + fetchedPages + "페이지, 공개 " + events.size() + "건"
            );
            return events;
        } catch (RuntimeException error) {
            logProviderFailure("SEOUL_CULTURE_API", error);
            recordSeoulCultureApiFailure("서울 문화행사 API 조회 실패");
            if (cached != null) {
                seoulCultureEventsCache.put(
                        cacheKey,
                        new CachedTourEvents(cached.events(), now.plus(lastGoodCacheDuration()))
                );
                return cached.events();
            }
            return List.of();
        }
    }

    private void logProviderFailure(String provider, RuntimeException error) {
        String httpStatus = error instanceof RestClientResponseException responseError
                ? String.valueOf(responseError.getStatusCode().value())
                : "n/a";
        log.warn(
                "외부 행사 API 조회 실패: provider={}, exception={}, httpStatus={}",
                provider,
                error.getClass().getSimpleName(),
                httpStatus
        );
    }

    private boolean isPublicEventApiSource(EventInsightItem event) {
        return "KTO_TOUR_API".equals(event.source()) || "SEOUL_CULTURE_API".equals(event.source());
    }

    private List<EventInsightItem> readFreshCachedEvents(Map<String, CachedTourEvents> cache) {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        return cache.values().stream()
                .flatMap(cached -> cached.events().stream())
                .toList();
    }

    private List<String> responseSources(List<EventInsightItem> events) {
        return events.stream()
                .map(EventInsightItem::source)
                .map(source -> switch (source) {
                    case "KTO_TOUR_API" -> "한국관광공사 국문 관광정보 서비스";
                    case "SEOUL_CULTURE_API" -> "서울특별시 문화행사 정보";
                    default -> null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<EventInsightItem> deduplicateEvents(List<EventInsightItem> events) {
        Map<String, EventInsightItem> uniqueEvents = new LinkedHashMap<>();
        for (EventInsightItem event : events) {
            uniqueEvents.putIfAbsent(eventIdentityKey(event), event);
        }
        return List.copyOf(uniqueEvents.values());
    }

    private List<EventInsightItem> prioritizeVerifiedPopupSources(List<EventInsightItem> events) {
        return events.stream()
                .sorted(Comparator.comparingInt(event -> hasVerifiedSeoulPopupSource(event) ? 0 : 1))
                .toList();
    }

    private boolean hasVerifiedSeoulPopupSource(EventInsightItem event) {
        String searchable = joinSearchable(event.title(), event.kind());
        return containsAnyTerm(searchable.toLowerCase(Locale.ROOT), EXPLICIT_POPUP_TERMS)
                && APPROVED_TREND_SOURCE.equals(event.source())
                && APPROVED_TREND_ATTRIBUTION.equals(event.attribution())
                && APPROVED_TREND_LICENSE.equals(event.license())
                && hasHost(event.sourceUrl(), "data.seoul.go.kr")
                && hasHost(event.licenseUrl(), "kogl.or.kr");
    }

    private String eventIdentityKey(EventInsightItem event) {
        String location;
        if (event.latitude() != null && event.longitude() != null) {
            location = String.format(Locale.ROOT, "%.4f:%.4f", event.latitude(), event.longitude());
        } else {
            location = normalizeEventIdentityPart(event.address());
        }
        return String.join(
                "|",
                normalizeEventIdentityPart(event.title()),
                normalizeProviderDate(event.startDate()),
                normalizeProviderDate(event.endDate()),
                location
        );
    }

    private String normalizeEventIdentityPart(String value) {
        return readString(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\p{P}\\p{S}]+", "");
    }

    private void recordTourApiSuccess(String detail) {
        tourApiAttempt.set(new ProviderAttempt(Instant.now(), null, detail));
    }

    private void recordTourApiFailure(String detail) {
        ProviderAttempt previous = tourApiAttempt.get();
        tourApiAttempt.set(new ProviderAttempt(
                previous != null ? previous.lastSuccessAt() : null,
                Instant.now(),
                detail
        ));
    }

    private void recordSeoulCultureApiSuccess(String detail) {
        seoulCultureApiAttempt.set(new ProviderAttempt(Instant.now(), null, detail));
    }

    private void recordSeoulCultureApiFailure(String detail) {
        ProviderAttempt previous = seoulCultureApiAttempt.get();
        seoulCultureApiAttempt.set(new ProviderAttempt(
                previous != null ? previous.lastSuccessAt() : null,
                Instant.now(),
                detail
        ));
    }

    private Duration cacheDuration() {
        return Duration.ofSeconds(Math.max(0, cacheTtlSeconds));
    }

    private Duration lastGoodCacheDuration() {
        return Duration.ofSeconds(Math.max(1, cacheTtlSeconds));
    }

    private int normalizedSeoulCulturePageSize() {
        return Math.max(1, Math.min(seoulCultureApiPageSize, SEOUL_CULTURE_MAX_PAGE_SIZE));
    }

    private int normalizedSeoulCultureMaxPages() {
        return Math.max(1, Math.min(seoulCultureApiMaxPages, SEOUL_CULTURE_HARD_MAX_PAGES));
    }

    private int requiredSeoulCulturePages(int totalCount, int pageSize) {
        if (totalCount <= 0) {
            return 1;
        }
        return Math.toIntExact(((long) totalCount + pageSize - 1L) / pageSize);
    }

    private String normalizedSeoulCultureBaseUrl() {
        if (seoulCultureApiBaseUrl == null || seoulCultureApiBaseUrl.isBlank()) {
            return "http://openapi.seoul.go.kr:8088";
        }
        return seoulCultureApiBaseUrl.trim();
    }

    private List<Map<String, Object>> readTourItems(Object response) {
        return firstMapList(
                readPath(response, "response", "body", "items", "item"),
                readPath(response, "response", "body", "items"),
                readPath(response, "response", "body", "item"),
                readPath(response, "body", "items", "item"),
                readPath(response, "body", "items"),
                readPath(response, "items", "item"),
                readPath(response, "items"),
                readPath(response, "item")
        );
    }

    private List<Map<String, Object>> readSeoulCultureItems(Object response) {
        return firstMapList(
                readPath(response, "culturalEventInfo", "row"),
                readPath(response, "culturalEventInfo", "ROW"),
                readPath(response, "row"),
                readPath(response, "ROW")
        );
    }

    private Integer readSeoulCultureTotalCount(Object response) {
        Object value = readPath(response, "culturalEventInfo", "list_total_count");
        if (value == null) {
            value = readPath(response, "culturalEventInfo", "LIST_TOTAL_COUNT");
        }
        if (value == null) {
            value = readPath(response, "list_total_count");
        }
        if (value == null) {
            value = readPath(response, "LIST_TOTAL_COUNT");
        }
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(readString(value).replace(",", ""));
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private EventInsightItem toEvent(Map<String, Object> item) {
        Double latitude = parseDouble(firstValue(item, "mapy", "latitude", "lat", "y"));
        Double longitude = parseDouble(firstValue(item, "mapx", "longitude", "lng", "lon", "x"));
        if (latitude == null || longitude == null) {
            return null;
        }

        String title = firstString(item, "title", "eventTitle", "name");
        if (title.isBlank()) {
            return null;
        }

        String contentId = firstString(item, "contentid", "contentId", "id");
        String startDate = firstString(item, "eventstartdate", "eventStartDate", "startDate", "startdate");
        String endDate = firstString(item, "eventenddate", "eventEndDate", "endDate", "enddate");
        String detailUrl = safeHttpUrl(firstValue(item, "eventhomepage", "homepage", "detailUrl", "url", "link"));
        String address = joinNonBlank(
                firstString(item, "addr1", "address", "location"),
                firstString(item, "addr2", "addressDetail")
        );
        String stableId = contentId.isBlank()
                ? "tour-" + Integer.toHexString(Objects.hash(title, startDate, address))
                : contentId;

        return new EventInsightItem(
                stableId,
                title,
                "festival",
                formatPeriod(startDate, endDate),
                startDate,
                endDate,
                latitude,
                longitude,
                address,
                nullableString(firstValue(item, "firstimage", "firstimage2", "imageUrl", "image")),
                "KTO_TOUR_API",
                readStringOrDefault(contentId, stableId),
                detailUrl,
                Instant.now().toString(),
                itemAttribution("KTO_TOUR_API", null)
        );
    }

    private EventInsightItem toSeoulCultureEvent(Map<String, Object> item) {
        Double latitude = parseDouble(firstValue(item, "LAT", "lat", "latitude", "mapy", "y"));
        Double longitude = parseDouble(firstValue(item, "LOT", "lot", "longitude", "lng", "lon", "mapx", "x"));
        if (latitude == null || longitude == null) {
            return null;
        }

        String title = firstString(item, "TITLE", "title", "name", "eventTitle");
        if (title.isBlank()) {
            return null;
        }

        String detailUrl = safeHttpUrl(firstValue(item, "ORG_LINK", "HMPG_ADDR", "detailUrl", "url", "link"));
        String sourceContentId = firstString(item, "CULTCODE", "cultcode", "CONTENT_ID", "contentId", "id");
        String startDate = normalizeProviderDate(firstString(item, "STRTDATE", "startDate", "eventStartDate", "start"));
        String endDate = normalizeProviderDate(firstString(item, "END_DATE", "endDate", "eventEndDate", "end"));
        String period = firstString(item, "DATE", "period", "date");
        String address = firstString(item, "PLACE", "place", "address", "GUNAME", "locationName");
        String providerKind = readStringOrDefault(firstValue(item, "CODENAME", "kind", "category"), "event");
        String stableId = "seoul-culture-" + Integer.toHexString(Objects.hash(title, startDate, address, detailUrl));

        return new EventInsightItem(
                stableId,
                title,
                canonicalizeSeoulCultureKind(title, providerKind),
                period.isBlank() ? formatPeriod(startDate, endDate) : period,
                startDate,
                endDate,
                latitude,
                longitude,
                address,
                nullableString(firstValue(item, "MAIN_IMG", "imageUrl", "image", "thumbnail")),
                "SEOUL_CULTURE_API",
                readStringOrDefault(sourceContentId, stableId),
                detailUrl,
                Instant.now().toString(),
                itemAttribution("SEOUL_CULTURE_API", null),
                APPROVED_TREND_SOURCE_URL,
                APPROVED_TREND_LICENSE,
                APPROVED_TREND_LICENSE_URL
        );
    }

    private boolean isPublishableSeongsuPopup(EventInsightItem event) {
        String locationSearchable = joinSearchable(event.title(), event.address());
        return containsAnyTerm(locationSearchable, SEONGSU_LOCATION_TERMS)
                && containsAnyTerm(event.title().toLowerCase(Locale.ROOT), EXPLICIT_POPUP_TERMS)
                && isWithinConfiguredSeongsuBounds(event.latitude(), event.longitude());
    }

    private boolean isSeongsuPopupIntent(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return containsAnyTerm(normalized, SEONGSU_POPUP_INTENT_TERMS)
                && containsAnyTerm(normalized, EXPLICIT_POPUP_TERMS);
    }

    private String canonicalizeSeoulCultureKind(String title, String providerKind) {
        return containsAnyTerm(title.toLowerCase(Locale.ROOT), EXPLICIT_POPUP_TERMS)
                ? "popup"
                : providerKind;
    }

    private String joinSearchable(String... values) {
        StringBuilder searchable = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!searchable.isEmpty()) {
                    searchable.append(' ');
                }
                searchable.append(value);
            }
        }
        return searchable.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAnyTerm(String searchable, List<String> terms) {
        return terms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(searchable::contains);
    }

    private boolean containsTobaccoOrNicotineTerms(Map<String, Object> item) {
        List<Object> metadata = new ArrayList<>();
        for (Map.Entry<String, Object> entry : item.entrySet()) {
            String normalizedKey = entry.getKey()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "");
            if (TOBACCO_METADATA_FIELDS.contains(normalizedKey)) {
                metadata.add(entry.getValue());
            }
        }
        return containsTobaccoOrNicotineTerms(metadata.toArray());
    }

    private boolean containsTobaccoOrNicotineTerms(EventInsightItem event) {
        return containsTobaccoOrNicotineTerms(
                event.title(),
                event.kind(),
                event.period(),
                event.address(),
                event.detailUrl(),
                event.sourceContentId()
        );
    }

    private boolean containsTobaccoOrNicotineTerms(Object... values) {
        StringBuilder searchable = new StringBuilder();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (!searchable.isEmpty()) {
                searchable.append(' ');
            }
            searchable.append(value);
        }
        String normalized = searchable.toString().toLowerCase(Locale.ROOT);
        return containsAnyTerm(normalized, TOBACCO_NICOTINE_TERMS)
                || TOBACCO_NICOTINE_ENGLISH_PATTERN.matcher(normalized).find();
    }

    private boolean isWithinConfiguredSeongsuBounds(Double latitude, Double longitude) {
        if (latitude == null
                || longitude == null
                || !Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || !Double.isFinite(seongsuMinLatitude)
                || !Double.isFinite(seongsuMaxLatitude)
                || !Double.isFinite(seongsuMinLongitude)
                || !Double.isFinite(seongsuMaxLongitude)
                || seongsuMinLatitude > seongsuMaxLatitude
                || seongsuMinLongitude > seongsuMaxLongitude) {
            return false;
        }
        return latitude >= seongsuMinLatitude
                && latitude <= seongsuMaxLatitude
                && longitude >= seongsuMinLongitude
                && longitude <= seongsuMaxLongitude;
    }

    private Object readPath(Object value, String... keys) {
        Object current = value;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstMapList(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof List<?> list) {
                List<Map<String, Object>> maps = list.stream()
                        .filter(Map.class::isInstance)
                        .map(value -> (Map<String, Object>) value)
                        .toList();
                if (!maps.isEmpty()) {
                    return maps;
                }
            }
            if (candidate instanceof Map<?, ?> map) {
                if (map.containsKey("item")) {
                    List<Map<String, Object>> nested = firstMapList(map.get("item"));
                    if (!nested.isEmpty()) {
                        return nested;
                    }
                    continue;
                }
                return List.of((Map<String, Object>) map);
            }
        }
        return List.of();
    }

    private EventInsightItem toPopupTrendEvent(Map<String, Object> item) {
        Double latitude = parseDouble(item.get("latitude"));
        Double longitude = parseDouble(item.get("longitude"));
        String id = readString(item.get("id"));
        String title = readString(item.get("title"));
        if (id.isBlank() || title.isBlank() || latitude == null || longitude == null) {
            return null;
        }

        String rawSourceContentId = nullableString(item.get("sourceContentId"));
        String explicitSourceContentId = firstString(item, "providerContentId", "contentId", "sourceId");
        String detailUrl = safeHttpUrl(firstValue(item, "detailUrl", "url", "link"));
        String legacyDetailUrl = safeHttpUrl(rawSourceContentId);
        if (detailUrl == null) {
            detailUrl = legacyDetailUrl;
        }

        String sourceContentId;
        if (!explicitSourceContentId.isBlank()) {
            sourceContentId = explicitSourceContentId;
        } else if (legacyDetailUrl != null || isUnsafeUrlCandidate(rawSourceContentId)) {
            sourceContentId = id;
        } else {
            sourceContentId = readStringOrDefault(rawSourceContentId, id);
        }

        String source = isManualTrendRecord(item)
                ? "MANUAL_SEED"
                : readStringOrDefault(item.get("source"), "CRAWLED_POPUP_TREND");
        String kind = readStringOrDefault(item.get("kind"), "popup");
        if ("SEOUL_CULTURE_API".equalsIgnoreCase(source)) {
            kind = canonicalizeSeoulCultureKind(title, kind);
        }

        return new EventInsightItem(
                id,
                title,
                kind,
                readString(item.get("period")),
                nullableString(item.get("startDate")),
                nullableString(item.get("endDate")),
                latitude,
                longitude,
                readString(item.get("address")),
                nullableString(item.get("imageUrl")),
                source,
                sourceContentId,
                detailUrl,
                nullableString(item.get("collectedAt")),
                itemAttribution(source, item.get("attribution")),
                safeHttpUrl(item.get("sourceUrl")),
                nullableString(item.get("license")),
                safeHttpUrl(item.get("licenseUrl"))
        );
    }

    private String itemAttribution(String source, Object explicitAttribution) {
        if ("KTO_TOUR_API".equalsIgnoreCase(source)) {
            return "한국관광공사";
        }
        if ("SEOUL_CULTURE_API".equalsIgnoreCase(source)) {
            return APPROVED_TREND_ATTRIBUTION;
        }

        String explicitLabel = nullableString(explicitAttribution);
        if (explicitLabel != null) {
            return explicitLabel;
        }
        if ("CRAWLED_POPUP_TREND".equalsIgnoreCase(source)) {
            return "팝업 공식 채널";
        }
        return "행사 정보 제공처";
    }

    private EventInsightItem toEligiblePopupTrendEvent(Map<String, Object> item) {
        if (!hasApprovedTrendPublicationRights(item)) {
            return null;
        }
        EventInsightItem event = toPopupTrendEvent(item);
        if (event == null || isManualTrendRecord(item)) {
            return null;
        }
        if ("popup".equalsIgnoreCase(event.kind()) && !isPublishableSeongsuPopup(event)) {
            return null;
        }
        if (parseEventDate(event.startDate()) == null
                || parseEventDate(event.endDate()) == null
                || safeHttpUrl(event.detailUrl()) == null
                || parseCollectedAt(event.collectedAt()) == null
                || isStaleCollectedEvent(event.collectedAt())) {
            return null;
        }
        return event;
    }

    private boolean hasApprovedTrendPublicationRights(Map<String, Object> item) {
        return APPROVED_TREND_SOURCE.equals(readString(item.get("source")))
                && "NETWORK".equals(readString(item.get("collectionMode")))
                && APPROVED_TREND_ATTRIBUTION.equals(readString(item.get("attribution")))
                && APPROVED_TREND_LICENSE.equals(readString(item.get("license")))
                && APPROVED_PUBLICATION_POLICY.equals(readString(item.get("publicationPolicy")))
                && VERIFIED_SOURCE_RIGHTS.equals(readString(item.get("verificationStatus")))
                && hasHost(item.get("sourceUrl"), "data.seoul.go.kr")
                && hasHost(item.get("licenseUrl"), "kogl.or.kr");
    }

    private boolean hasHost(Object value, String expectedHost) {
        String safeUrl = safeHttpUrl(value);
        if (safeUrl == null) {
            return false;
        }
        try {
            String host = URI.create(safeUrl).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
            return normalizedHost.equals(expectedHost) || normalizedHost.endsWith("." + expectedHost);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isManualTrendRecord(Map<String, Object> item) {
        String collectionMode = readString(item.get("collectionMode"));
        String source = readString(item.get("source"));
        return "MANUAL".equalsIgnoreCase(collectionMode)
                || "MANUAL".equalsIgnoreCase(source)
                || "MANUAL_SEED".equalsIgnoreCase(source);
    }

    private boolean hasVerifiableEventMetadata(EventInsightItem event) {
        LocalDate startDate = parseEventDate(event.startDate());
        LocalDate endDate = parseEventDate(event.endDate());
        return startDate != null
                && endDate != null
                && !startDate.isAfter(endDate)
                && event.address() != null
                && !event.address().isBlank()
                && safeHttpUrl(event.detailUrl()) != null;
    }

    private boolean matches(EventInsightItem event, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalized = keyword.toLowerCase(Locale.ROOT).trim();
        List<String> searchableValues = List.of(event.id(), event.title(), event.kind(), event.address(), event.period()).stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        if (searchableValues.stream().anyMatch(value -> value.contains(normalized))) {
            return true;
        }

        List<String> specificTerms = specificEventSearchTerms(normalized);
        if (specificTerms.isEmpty()) {
            return containsGenericEventKeyword(normalized);
        }
        return specificTerms.stream()
                .allMatch(term -> searchableValues.stream().anyMatch(value -> value.contains(term)));
    }

    private List<String> specificEventSearchTerms(String keyword) {
        String reduced = keyword;
        for (String genericKeyword : GENERIC_EVENT_KEYWORDS) {
            reduced = reduced.replace(genericKeyword.toLowerCase(Locale.ROOT), " ");
        }
        return List.of(reduced.split("[\\s,./|·]+")).stream()
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .toList();
    }

    private boolean containsGenericEventKeyword(String keyword) {
        return GENERIC_EVENT_KEYWORDS.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(keyword::contains);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 20));
    }

    private String formatPeriod(String startDate, String endDate) {
        if (startDate == null || startDate.isBlank()) {
            return "";
        }

        if (endDate == null || endDate.isBlank() || startDate.equals(endDate)) {
            return startDate;
        }

        return startDate + "-" + endDate;
    }

    private Comparator<EventInsightItem> eventPriorityComparator() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        return Comparator
                .comparingInt((EventInsightItem event) -> eventStatusRank(event, today))
                .thenComparing(event -> daysUntilStart(event, today))
                .thenComparing(EventInsightItem::title, Comparator.nullsLast(String::compareTo));
    }

    private int eventStatusRank(EventInsightItem event, LocalDate today) {
        LocalDate startDate = parseEventDate(event.startDate());
        LocalDate endDate = parseEventDate(event.endDate());

        if (startDate == null && endDate == null) {
            return 2;
        }
        if (startDate == null) {
            return endDate != null && !endDate.isBefore(today) ? 0 : 3;
        }
        if (endDate == null) {
            return startDate.isBefore(today) ? 0 : 1;
        }
        if (!startDate.isAfter(today) && !endDate.isBefore(today)) {
            return 0;
        }
        return startDate.isAfter(today) ? 1 : 3;
    }

    private boolean isCurrentOrUpcomingEvent(EventInsightItem event) {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        LocalDate startDate = parseEventDate(event.startDate());
        LocalDate endDate = parseEventDate(event.endDate());

        if (endDate != null) {
            return !endDate.isBefore(today);
        }
        if (startDate != null) {
            return !startDate.isBefore(today);
        }
        return true;
    }

    private long daysUntilStart(EventInsightItem event, LocalDate today) {
        LocalDate startDate = parseEventDate(event.startDate());
        if (startDate == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs(Duration.between(today.atStartOfDay(), startDate.atStartOfDay()).toDays());
    }

    private LocalDate parseEventDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() >= 10 && Character.isDigit(trimmed.charAt(0))) {
            String datePrefix = trimmed.substring(0, 10);
            try {
                return LocalDate.parse(datePrefix, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // Try the provider-specific formats below.
            }
        }
        for (DateTimeFormatter formatter : List.of(TOUR_DATE, DateTimeFormatter.ISO_LOCAL_DATE, DOTTED_DATE)) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next known provider format.
            }
        }
        return null;
    }

    private String normalizeProviderDate(String value) {
        LocalDate date = parseEventDate(value);
        return date == null ? readString(value) : date.toString();
    }

    private boolean isStaleCollectedEvent(String value) {
        Instant collectedAt = parseCollectedAt(value);
        if (collectedAt == null) {
            return false;
        }
        return collectedAt.isBefore(Instant.now().minus(Duration.ofHours(normalizedPopupTrendsMaxAgeHours())));
    }

    private long normalizedPopupTrendsMaxAgeHours() {
        return popupTrendsMaxAgeHours > 0 ? popupTrendsMaxAgeHours : 24L;
    }

    private Instant parseCollectedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value.trim()).toInstant();
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private String safeHttpUrl(Object value) {
        String candidate = nullableString(value);
        if (candidate == null) {
            return null;
        }
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.isAbsolute()
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && isPublicLinkHost(uri.getHost())) {
                return candidate;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URLs are intentionally omitted from the public response.
        }
        return null;
    }

    private boolean isPublicLinkHost(String value) {
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        host = host.replaceFirst("\\.$", "");
        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")) {
            return false;
        }

        // Literal IPv6 (including IPv4-mapped IPv6) is intentionally not exposed.
        // Public provider links use DNS names, so failing closed avoids alternate
        // loopback/private spellings across URI consumers.
        if (host.contains(":")) {
            return false;
        }

        Long ipv4 = parseIpv4Literal(host);
        if (ipv4 == null) {
            return !looksLikeIpv4Literal(host);
        }
        int first = (int) ((ipv4 >>> 24) & 0xff);
        int second = (int) ((ipv4 >>> 16) & 0xff);
        return first != 0
                && first != 10
                && first != 127
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168)
                && first < 224;
    }

    private boolean looksLikeIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length < 1 || parts.length > 4) {
            return false;
        }
        for (String part : parts) {
            if (!part.matches("(?i)(?:0x[0-9a-f]+|[0-9]+)")) {
                return false;
            }
        }
        return true;
    }

    private Long parseIpv4Literal(String host) {
        if (!looksLikeIpv4Literal(host)) {
            return null;
        }
        String[] parts = host.split("\\.", -1);
        long[] parsed = new long[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                parsed[index] = parseIpv4Component(parts[index]);
            }
        } catch (NumberFormatException invalid) {
            return null;
        }

        long address;
        switch (parsed.length) {
            case 1 -> address = parsed[0];
            case 2 -> {
                if (parsed[0] > 0xffL || parsed[1] > 0xffffffL) {
                    return null;
                }
                address = (parsed[0] << 24) | parsed[1];
            }
            case 3 -> {
                if (parsed[0] > 0xffL || parsed[1] > 0xffL || parsed[2] > 0xffffL) {
                    return null;
                }
                address = (parsed[0] << 24) | (parsed[1] << 16) | parsed[2];
            }
            case 4 -> {
                for (long component : parsed) {
                    if (component > 0xffL) {
                        return null;
                    }
                }
                address = (parsed[0] << 24) | (parsed[1] << 16) | (parsed[2] << 8) | parsed[3];
            }
            default -> {
                return null;
            }
        }
        return address >= 0 && address <= 0xffffffffL ? address : null;
    }

    private long parseIpv4Component(String component) {
        if (component.startsWith("0x") || component.startsWith("0X")) {
            return Long.parseLong(component.substring(2), 16);
        }
        if (component.length() > 1 && component.startsWith("0")) {
            return Long.parseLong(component.substring(1), 8);
        }
        return Long.parseLong(component, 10);
    }

    private boolean isUnsafeUrlCandidate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("javascript:")
                || normalized.startsWith("data:")
                || normalized.startsWith("file:")
                || normalized.startsWith("vbscript:")
                || normalized.contains("://")
                || normalized.startsWith("//");
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String readStringOrDefault(Object value, String fallback) {
        String parsed = readString(value);
        return parsed.isBlank() ? fallback : parsed;
    }

    private String nullableString(Object value) {
        String parsed = readString(value);
        return parsed.isBlank() ? null : parsed;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }

        try {
            String normalized = String.valueOf(value).trim().replace(",", "");
            return Double.parseDouble(normalized);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Object firstValue(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !readString(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> item, String... keys) {
        return readString(firstValue(item, keys));
    }

    private String joinNonBlank(String first, String second) {
        if (first == null || first.isBlank()) {
            return readString(second);
        }
        if (second == null || second.isBlank()) {
            return readString(first);
        }
        return first.trim() + " " + second.trim();
    }

    private record Bounds(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        static Bounds of(Double minLat, Double maxLat, Double minLng, Double maxLng) {
            if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
                return new Bounds(null, null, null, null);
            }
            return new Bounds(
                    Math.min(minLat, maxLat),
                    Math.max(minLat, maxLat),
                    Math.min(minLng, maxLng),
                    Math.max(minLng, maxLng)
            );
        }

        boolean contains(Double latitude, Double longitude) {
            if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
                return true;
            }
            if (latitude == null || longitude == null) {
                return false;
            }
            return latitude >= minLat && latitude <= maxLat && longitude >= minLng && longitude <= maxLng;
        }
    }

    private record CachedTourEvents(List<EventInsightItem> events, Instant expiresAt) {
    }

    private record ProviderAttempt(Instant lastSuccessAt, Instant lastFailureAt, String detail) {
    }
}
