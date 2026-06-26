package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.EventInsightResponse;
import com.neogulmap.neogul_map.dto.EventInsightResponse.EventInsightItem;
import com.neogulmap.neogul_map.dto.InsightStatusResponse.ProviderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class EventInsightService {

    private static final DateTimeFormatter TOUR_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DOTTED_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
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
    private static final List<String> SOURCES = List.of(
            "한국관광공사 국문 관광정보 서비스",
            "서울 문화행사 API",
            "서울 팝업·핫플 후보 레지스트리"
    );
    private static final List<EventInsightItem> FALLBACK_EVENTS = List.of(
            new EventInsightItem(
                    "popup-seongsu-weekend",
                    "성수 팝업 스토어 밀집 구간",
                    "popup",
                    "상시 후보",
                    null,
                    null,
                    37.5446,
                    127.0557,
                    "서울 성동구 성수동2가",
                    null,
                    "STATIC_EVENT_SEED",
                    "seongsu-popup"
            ),
            new EventInsightItem(
                    "festival-yeouido-river",
                    "여의도 한강공원 행사·축제 후보",
                    "festival",
                    "계절 행사 후보",
                    null,
                    null,
                    37.5284,
                    126.9327,
                    "서울 영등포구 여의동로 330",
                    null,
                    "STATIC_EVENT_SEED",
                    "yeouido-river"
            ),
            new EventInsightItem(
                    "market-hongdae",
                    "홍대 거리 공연·플리마켓 후보",
                    "street_event",
                    "주말 후보",
                    null,
                    null,
                    37.5563,
                    126.9236,
                    "서울 마포구 홍대입구역 일대",
                    null,
                    "STATIC_EVENT_SEED",
                    "hongdae-market"
            )
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

    @Value("${external.seoul.culture-api-end-index:${SEOUL_CULTURE_API_END_INDEX:20}}")
    private int seoulCultureApiEndIndex;

    @Value("${external.popup-trends.file:${POPUP_TRENDS_FILE:}}")
    private String popupTrendsFile;

    @Value("${external.insights.cache-ttl-seconds:${INSIGHTS_CACHE_TTL_SECONDS:180}}")
    private long cacheTtlSeconds;

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
        liveEvents.addAll(fetchTourEvents(limit));
        liveEvents.addAll(fetchSeoulCultureEvents(limit));
        liveEvents.addAll(readPopupTrendEvents());

        List<EventInsightItem> normalizedLiveEvents = liveEvents.stream()
                .filter(this::isCurrentOrUpcomingEvent)
                .filter(event -> matches(event, keyword))
                .filter(event -> bounds.contains(event.latitude(), event.longitude()))
                .sorted(eventPriorityComparator())
                .limit(normalizeLimit(limit))
                .toList();

        if (!normalizedLiveEvents.isEmpty()) {
            String freshness = normalizedLiveEvents.stream().anyMatch(this::isPublicEventApiSource)
                    ? "LIVE_OR_PARTIAL"
                    : "CRAWLED_OR_PARTIAL";
            return new EventInsightResponse(normalizedLiveEvents, freshness, Instant.now(), SOURCES);
        }

        List<EventInsightItem> fallbackEvents = FALLBACK_EVENTS.stream()
                .filter(event -> matches(event, keyword))
                .filter(event -> bounds.contains(event.latitude(), event.longitude()))
                .sorted(eventPriorityComparator())
                .limit(normalizeLimit(limit))
                .toList();

        return new EventInsightResponse(fallbackEvents, "STATIC_FALLBACK", Instant.now(), SOURCES);
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
                    .map(this::toPopupTrendEvent)
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
        String cacheKey = TOUR_DATE.format(LocalDate.now()) + ":" + normalizedLimit;
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
                    .queryParam("eventStartDate", TOUR_DATE.format(LocalDate.now()))
                    .queryParam("numOfRows", normalizedLimit)
                    .queryParam("pageNo", "1")
                    .build(false)
                    .toUriString();

            Object response = restTemplate.getForObject(url, Object.class);
            List<EventInsightItem> events = readTourItems(response).stream()
                    .map(this::toEvent)
                    .filter(Objects::nonNull)
                    .toList();
            tourEventsCache.put(cacheKey, new CachedTourEvents(events, now.plus(cacheDuration())));
            recordTourApiSuccess("한국관광공사 행사정보 조회 성공: " + events.size() + "건");
            return events;
        } catch (RuntimeException error) {
            log.warn("한국관광공사 행사정보 조회 실패: {}", error.getMessage());
            recordTourApiFailure("한국관광공사 행사정보 조회 실패");
            return List.of();
        }
    }

    private List<EventInsightItem> fetchSeoulCultureEvents(int limit) {
        if (seoulCultureApiKey == null || seoulCultureApiKey.isBlank()) {
            return List.of();
        }

        int normalizedLimit = Math.max(1, Math.min(Math.max(limit, seoulCultureApiEndIndex), 50));
        String cacheKey = TOUR_DATE.format(LocalDate.now()) + ":" + normalizedLimit;
        Instant now = Instant.now();
        CachedTourEvents cached = seoulCultureEventsCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.events();
        }

        try {
            String url = UriComponentsBuilder
                    .fromUriString(normalizedSeoulCultureBaseUrl())
                    .pathSegment(seoulCultureApiKey.trim(), "json", "culturalEventInfo", "1", String.valueOf(normalizedLimit))
                    .build(false)
                    .toUriString();

            Object response = restTemplate.getForObject(url, Object.class);
            List<EventInsightItem> events = readSeoulCultureItems(response).stream()
                    .map(this::toSeoulCultureEvent)
                    .filter(Objects::nonNull)
                    .toList();
            seoulCultureEventsCache.put(cacheKey, new CachedTourEvents(events, now.plus(cacheDuration())));
            recordSeoulCultureApiSuccess("서울 문화행사 API 조회 성공: " + events.size() + "건");
            return events;
        } catch (RuntimeException error) {
            log.warn("서울 문화행사 API 조회 실패: {}", error.getMessage());
            recordSeoulCultureApiFailure("서울 문화행사 API 조회 실패");
            return List.of();
        }
    }

    private boolean isPublicEventApiSource(EventInsightItem event) {
        return "KTO_TOUR_API".equals(event.source()) || "SEOUL_CULTURE_API".equals(event.source());
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
                readStringOrDefault(contentId, stableId)
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

        String link = firstString(item, "HMPG_ADDR", "ORG_LINK", "url", "link");
        String startDate = normalizeProviderDate(firstString(item, "STRTDATE", "startDate", "eventStartDate", "start"));
        String endDate = normalizeProviderDate(firstString(item, "END_DATE", "endDate", "eventEndDate", "end"));
        String period = firstString(item, "DATE", "period", "date");
        String address = firstString(item, "PLACE", "place", "address", "GUNAME", "locationName");
        String stableId = "seoul-culture-" + Integer.toHexString(Objects.hash(title, startDate, address, link));

        return new EventInsightItem(
                stableId,
                title,
                readStringOrDefault(firstValue(item, "CODENAME", "kind", "category"), "event"),
                period.isBlank() ? formatPeriod(startDate, endDate) : period,
                startDate,
                endDate,
                latitude,
                longitude,
                address,
                nullableString(firstValue(item, "MAIN_IMG", "imageUrl", "image", "thumbnail")),
                "SEOUL_CULTURE_API",
                readStringOrDefault(link, stableId)
        );
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

        return new EventInsightItem(
                id,
                title,
                readStringOrDefault(item.get("kind"), "popup"),
                readString(item.get("period")),
                nullableString(item.get("startDate")),
                nullableString(item.get("endDate")),
                latitude,
                longitude,
                readString(item.get("address")),
                nullableString(item.get("imageUrl")),
                readStringOrDefault(item.get("source"), "CRAWLED_POPUP_TREND"),
                readStringOrDefault(item.get("sourceContentId"), id)
        );
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
        LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();
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
