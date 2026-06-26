package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.dto.HotplaceResponse;
import com.neogulmap.neogul_map.dto.HotplaceResponse.HotplaceItem;
import com.neogulmap.neogul_map.dto.InsightStatusResponse.ProviderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class HotplaceService {

    private static final List<HotplaceSeed> SEOUL_SEEDS = List.of(
            new HotplaceSeed("lotte-world", "POI005", "잠실 관광특구", "롯데월드·잠실", "theme_park", 37.5111, 127.0982, "서울 송파구 올림픽로 240",
                    List.of("POI005", "잠실 관광특구", "POI118", "잠실새내역", "POI109", "잠실종합운동장")),
            new HotplaceSeed("lotte-tower-lake", "POI005", "잠실 관광특구", "잠실롯데타워·석촌호수", "landmark", 37.5130, 127.1035, "서울 송파구 올림픽로 300",
                    List.of("POI005", "잠실 관광특구", "POI118", "잠실새내역", "POI109", "잠실종합운동장")),
            new HotplaceSeed("hongdae", "POI007", "홍대 관광특구", "홍대입구", "hot_street", 37.5563, 126.9236, "서울 마포구 홍대입구역 일대"),
            new HotplaceSeed("seongsu", "POI068", "성수카페거리", "성수동 카페거리", "popup", 37.5446, 127.0557, "서울 성동구 성수동2가"),
            new HotplaceSeed("yeouido", "POI105", "여의도한강공원", "여의도 한강공원", "festival", 37.5284, 126.9327, "서울 영등포구 여의동로 330"),
            new HotplaceSeed("gwanghwamun", "POI009", "광화문·덕수궁", "광화문·덕수궁", "landmark", 37.5759, 126.9768, "서울 종로구 세종대로"),
            new HotplaceSeed("gangnam-station", "POI014", "강남역", "강남역", "transit_hotspot", 37.4979, 127.0276, "서울 강남구 강남대로 지하396"),
            new HotplaceSeed("myeongdong", "POI003", "명동 관광특구", "명동", "shopping", 37.5636, 126.9822, "서울 중구 명동"),
            new HotplaceSeed("itaewon", "POI004", "이태원 관광특구", "이태원", "nightlife", 37.5345, 126.9946, "서울 용산구 이태원동"),
            new HotplaceSeed("dongdaemun", "POI002", "동대문 관광특구", "동대문", "shopping", 37.5665, 127.0090, "서울 중구 을지로6가"),
            new HotplaceSeed("gangnam-mice", "POI001", "강남 MICE 관광특구", "코엑스·강남 MICE", "exhibition", 37.5118, 127.0592, "서울 강남구 영동대로 513"),
            new HotplaceSeed("apgujeong-rodeo", "POI071", "압구정로데오거리", "압구정로데오거리", "shopping", 37.5274, 127.0409, "서울 강남구 압구정로데오역 일대"),
            new HotplaceSeed("garosu-gil", "POI059", "가로수길", "가로수길", "shopping", 37.5208, 127.0227, "서울 강남구 신사동"),
            new HotplaceSeed("yeonnam", "POI073", "연남동", "연남동", "cafe_street", 37.5627, 126.9219, "서울 마포구 연남동"),
            new HotplaceSeed("ikseon", "POI078", "인사동·익선동", "익선동", "cafe_street", 37.5743, 126.9899, "서울 종로구 익선동"),
            new HotplaceSeed("insadong", "POI078", "인사동·익선동", "인사동", "culture_street", 37.5740, 126.9853, "서울 종로구 인사동"),
            new HotplaceSeed("bukchon", "POI066", "북촌한옥마을", "북촌한옥마을", "culture_street", 37.5826, 126.9836, "서울 종로구 북촌로"),
            new HotplaceSeed("songridan", null, "송리단길·호수단길", "송리단길·호수단길", "food_street", 37.5100, 127.1082, "서울 송파구 송파동"),
            new HotplaceSeed("ddp", "POI083", "DDP(동대문디자인플라자)", "DDP", "exhibition", 37.5663, 127.0094, "서울 중구 을지로 281"),
            new HotplaceSeed("gwangjang-market", "POI060", "광장(전통)시장", "광장시장", "market", 37.5700, 126.9993, "서울 종로구 창경궁로 88"),
            new HotplaceSeed("times-square", "POI074", "영등포 타임스퀘어", "영등포 타임스퀘어", "shopping", 37.5171, 126.9034, "서울 영등포구 영중로 15")
    );

    private static final List<String> SOURCES = List.of(
            "서울특별시_실시간 도시데이터(통신 기지국 기반 실시간 인구 추정)",
            "한국관광공사 TourAPI 후보 연동",
            "SKT 장소 혼잡도 후보 연동"
    );
    private static final List<String> HOT_NOW_KEYWORDS = List.of("hot-now", "hot_now", "지금 핫한 곳", "핫플");
    private static final List<String> CITYWIDE_HOT_QUERY_FRAGMENTS = List.of("hot-now", "hot_now", "지금핫", "핫플", "핫한곳");
    private static final List<String> CROWD_QUERY_FRAGMENTS = List.of("혼잡", "사람많", "붐빔", "붐비", "인파", "crowd", "busy");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d[\\d,]*");
    private static final Pattern SEARCH_NORMALIZE_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    private final org.springframework.web.client.RestTemplate restTemplate;
    private final Map<String, CachedHotplace> cityDataCache = new ConcurrentHashMap<>();
    private final Map<String, CachedHotplace> telecomCrowdCache = new ConcurrentHashMap<>();
    private final AtomicReference<ProviderAttempt> seoulCityDataAttempt = new AtomicReference<>();
    private final AtomicReference<ProviderAttempt> telecomCrowdAttempt = new AtomicReference<>();

    @Value("${external.seoul.citydata.api-key:${SEOUL_CITYDATA_API_KEY:}}")
    private String seoulCityDataApiKey;

    @Value("${external.telecom.crowd.api-key:${TELECOM_CROWD_API_KEY:}}")
    private String telecomCrowdApiKey;

    @Value("${external.telecom.crowd.url-template:${TELECOM_CROWD_URL_TEMPLATE:}}")
    private String telecomCrowdUrlTemplate;

    @Value("${external.telecom.crowd.api-key-header:${TELECOM_CROWD_API_KEY_HEADER:appKey}}")
    private String telecomCrowdApiKeyHeader;

    @Value("${external.insights.cache-ttl-seconds:${INSIGHTS_CACHE_TTL_SECONDS:180}}")
    private long cacheTtlSeconds;

    public HotplaceService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    public HotplaceResponse getHotplaces(String keyword, int limit) {
        return getHotplaces(keyword, limit, null, null, null, null);
    }

    public HotplaceResponse getHotplaces(
            String keyword,
            int limit,
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng
    ) {
        Bounds bounds = Bounds.of(minLat, maxLat, minLng, maxLng);
        List<HotplaceItem> places = SEOUL_SEEDS.stream()
                .filter(seed -> matches(seed, keyword))
                .filter(seed -> bounds.contains(seed.latitude(), seed.longitude()))
                .map(this::resolveHotplaceSignal)
                .sorted(Comparator
                        .comparingInt(this::crowdRank).reversed()
                        .thenComparing(Comparator.comparingInt(this::estimatedPeopleRank).reversed())
                        .thenComparing(Comparator.comparingInt(this::fallbackHotRank).reversed()))
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();

        String freshness = places.stream().anyMatch(place -> "SEOUL_CITYDATA".equals(place.source()) || "TELECOM_CROWD".equals(place.source()))
                ? "LIVE_OR_PARTIAL"
                : "STATIC_FALLBACK";

        return new HotplaceResponse(places, freshness, Instant.now(), SOURCES);
    }

    public ProviderStatus getSeoulCityDataProviderStatus() {
        if (seoulCityDataApiKey == null || seoulCityDataApiKey.isBlank()) {
            return ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured");
        }

        ProviderAttempt attempt = seoulCityDataAttempt.get();
        if (attempt == null) {
            return ProviderStatus.configuredUnverified("서울 실시간 도시데이터 키는 설정됐지만 이번 런타임에서 아직 조회 성공 여부를 확인하지 않았습니다.");
        }
        if (attempt.lastFailureAt() == null || (attempt.lastSuccessAt() != null && attempt.lastSuccessAt().isAfter(attempt.lastFailureAt()))) {
            return ProviderStatus.ok(attempt.lastSuccessAt(), attempt.detail());
        }
        return ProviderStatus.error(attempt.lastSuccessAt(), attempt.lastFailureAt(), attempt.detail());
    }

    public ProviderStatus getTelecomCrowdProviderStatus() {
        if (telecomCrowdApiKey == null || telecomCrowdApiKey.isBlank()) {
            return ProviderStatus.notConfigured("TELECOM_CROWD_API_KEY is not configured");
        }
        if (telecomCrowdUrlTemplate == null || telecomCrowdUrlTemplate.isBlank()) {
            return ProviderStatus.configuredUnverified("통신사 혼잡도 키는 설정됐지만 TELECOM_CROWD_URL_TEMPLATE이 없어 조회를 시작하지 않았습니다.");
        }

        ProviderAttempt attempt = telecomCrowdAttempt.get();
        if (attempt == null) {
            return ProviderStatus.configuredUnverified("통신사 혼잡도 어댑터는 설정됐지만 이번 런타임에서 아직 조회 성공 여부를 확인하지 않았습니다.");
        }
        if (attempt.lastFailureAt() == null || (attempt.lastSuccessAt() != null && attempt.lastSuccessAt().isAfter(attempt.lastFailureAt()))) {
            return ProviderStatus.ok(attempt.lastSuccessAt(), attempt.detail());
        }
        return ProviderStatus.error(attempt.lastSuccessAt(), attempt.lastFailureAt(), attempt.detail());
    }

    private HotplaceItem resolveHotplaceSignal(HotplaceSeed seed) {
        return liveCrowdSignalProviders().stream()
                .map(provider -> provider.fetch(seed))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> fallback(seed));
    }

    private List<CrowdSignalProvider> liveCrowdSignalProviders() {
        return List.of(new TelecomCrowdSignalProvider(), new SeoulCityDataCrowdSignalProvider());
    }

    private Optional<HotplaceItem> fetchTelecomCrowd(HotplaceSeed seed) {
        if (telecomCrowdApiKey == null || telecomCrowdApiKey.isBlank()
                || telecomCrowdUrlTemplate == null || telecomCrowdUrlTemplate.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        CachedHotplace cached = telecomCrowdCache.get(seed.id());
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(cached.item());
        }

        try {
            URI uri = URI.create(resolveTelecomCrowdUrl(seed));
            HttpHeaders headers = new HttpHeaders();
            if (shouldSendTelecomApiKeyHeader()) {
                headers.set(telecomCrowdApiKeyHeader.trim(), telecomCrowdApiKey.trim());
            }
            RequestEntity<Void> request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    request,
                    new ParameterizedTypeReference<>() {
                    }
            );
            HotplaceItem parsed = parseTelecomCrowd(seed, response.getBody());
            if (parsed != null) {
                telecomCrowdCache.put(seed.id(), new CachedHotplace(parsed, now.plus(cacheDuration())));
                recordTelecomCrowdSuccess("통신사 혼잡도 조회 성공: " + seed.displayName());
                return Optional.of(parsed);
            }
            recordTelecomCrowdFailure("통신사 혼잡도 응답에 필요한 혼잡도 값이 없습니다: " + seed.displayName());
            return Optional.empty();
        } catch (RuntimeException error) {
            log.warn("통신사 혼잡도 조회 실패: {}", seed.displayName());
            recordTelecomCrowdFailure("통신사 혼잡도 조회 실패: " + seed.displayName());
            return Optional.empty();
        }
    }

    private String resolveTelecomCrowdUrl(HotplaceSeed seed) {
        return telecomCrowdUrlTemplate.trim()
                .replace("{placeId}", urlEncode(seed.id()))
                .replace("{placeName}", urlEncode(seed.displayName()))
                .replace("{seoulAreaCode}", urlEncode(areaCodeOrName(seed)))
                .replace("{seoulAreaName}", urlEncode(seed.seoulAreaName()))
                .replace("{apiKey}", urlEncode(telecomCrowdApiKey.trim()))
                .replace("{lat}", String.valueOf(seed.latitude()))
                .replace("{lng}", String.valueOf(seed.longitude()));
    }

    private boolean shouldSendTelecomApiKeyHeader() {
        return telecomCrowdApiKeyHeader != null
                && !telecomCrowdApiKeyHeader.isBlank()
                && !"none".equalsIgnoreCase(telecomCrowdApiKeyHeader.trim())
                && !"off".equalsIgnoreCase(telecomCrowdApiKeyHeader.trim());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HotplaceItem parseTelecomCrowd(HotplaceSeed seed, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Map<String, Object> data = telecomCrowdDataCandidates(payload).stream()
                .filter(this::hasCrowdSignal)
                .findFirst()
                .orElse(payload);
        String crowdLevel = firstString(data, List.of("crowdLevel", "congestionLevel", "congestion", "level"));
        Integer minPeople = firstInteger(data, List.of("estimatedMinPeople", "minPeople", "populationMin", "peopleMin"));
        Integer maxPeople = firstInteger(data, List.of("estimatedMaxPeople", "maxPeople", "populationMax", "peopleMax"));
        PopulationRange populationRange = firstPopulationRange(data, List.of("estimatedPeople", "population", "populationRange", "people", "peopleRange", "visitorCount"));
        if (minPeople == null) {
            minPeople = populationRange.minPeople();
        }
        if (maxPeople == null) {
            maxPeople = populationRange.maxPeople();
        }
        String message = firstString(data, List.of("crowdMessage", "message", "congestionMessage"));
        String updatedAt = firstString(data, List.of("updatedAt", "baseTime", "timestamp", "datetime"));
        String placeCode = firstString(data, List.of("sourcePlaceCode", "placeId", "poiId", "id"));

        if ((crowdLevel == null || crowdLevel.isBlank()) && minPeople == null && maxPeople == null) {
            return null;
        }

        return new HotplaceItem(
                seed.id(),
                seed.displayName(),
                seed.category(),
                crowdLevel == null || crowdLevel.isBlank() ? "UNKNOWN" : normalizeCrowdLevel(crowdLevel),
                message == null || message.isBlank() ? "통신사 기반 장소 혼잡도 신호입니다." : message,
                minPeople,
                maxPeople,
                seed.latitude(),
                seed.longitude(),
                seed.address(),
                "TELECOM_CROWD",
                placeCode == null || placeCode.isBlank() ? seed.id() : placeCode,
                updatedAt
        );
    }

    private Optional<HotplaceItem> fetchSeoulCityData(HotplaceSeed seed) {
        if (seoulCityDataApiKey == null || seoulCityDataApiKey.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        CachedHotplace cached = cityDataCache.get(seed.id());
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(cached.item());
        }

        for (String query : seed.cityDataQueries()) {
            try {
                URI uri = UriComponentsBuilder
                        .fromUriString("http://openapi.seoul.go.kr:8088")
                        .pathSegment(seoulCityDataApiKey.trim(), "xml", "citydata", "1", "5", query)
                        .build()
                        .encode(StandardCharsets.UTF_8)
                        .toUri();
                String xml = restTemplate.getForObject(uri, String.class);
                HotplaceItem parsed = parseCityData(seed, xml);
                if (parsed != null) {
                    cityDataCache.put(seed.id(), new CachedHotplace(parsed, now.plus(cacheDuration())));
                    recordSeoulCityDataSuccess("서울 실시간 도시데이터 조회 성공: " + seed.seoulAreaName() + " / " + query);
                    return Optional.of(parsed);
                }
                if (cityDataXmlLooksLikeKnownArea(xml)) {
                    recordSeoulCityDataFailure("서울 실시간 도시데이터 응답에 혼잡도 값이 없습니다: " + seed.seoulAreaName() + " / " + query);
                    return Optional.empty();
                }
            } catch (RuntimeException error) {
                log.warn("서울 실시간 도시데이터 조회 실패: {} / {}", seed.seoulAreaName(), query);
            }
        }
        recordSeoulCityDataFailure("서울 실시간 도시데이터 조회 실패: " + seed.seoulAreaName());
        return Optional.empty();
    }

    private void recordSeoulCityDataSuccess(String detail) {
        seoulCityDataAttempt.set(new ProviderAttempt(Instant.now(), null, detail));
    }

    private void recordSeoulCityDataFailure(String detail) {
        ProviderAttempt previous = seoulCityDataAttempt.get();
        seoulCityDataAttempt.set(new ProviderAttempt(
                previous != null ? previous.lastSuccessAt() : null,
                Instant.now(),
                detail
        ));
    }

    private void recordTelecomCrowdSuccess(String detail) {
        telecomCrowdAttempt.set(new ProviderAttempt(Instant.now(), null, detail));
    }

    private void recordTelecomCrowdFailure(String detail) {
        ProviderAttempt previous = telecomCrowdAttempt.get();
        telecomCrowdAttempt.set(new ProviderAttempt(
                previous != null ? previous.lastSuccessAt() : null,
                Instant.now(),
                detail
        ));
    }

    private String areaCodeOrName(HotplaceSeed seed) {
        return seed.seoulAreaCode() == null || seed.seoulAreaCode().isBlank()
                ? seed.seoulAreaName()
                : seed.seoulAreaCode();
    }

    private Duration cacheDuration() {
        return Duration.ofSeconds(Math.max(0, cacheTtlSeconds));
    }

    private HotplaceItem parseCityData(HotplaceSeed seed, String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();

            String areaName = firstText(root, "AREA_NM");
            String level = firstText(root, "AREA_CONGEST_LVL");
            String message = firstText(root, "AREA_CONGEST_MSG");
            Integer minPeople = parseInteger(firstText(root, "AREA_PPLTN_MIN"));
            Integer maxPeople = parseInteger(firstText(root, "AREA_PPLTN_MAX"));
            String updateTime = firstText(root, "PPLTN_TIME");

            if (level == null || level.isBlank()) {
                return null;
            }

            return new HotplaceItem(
                    seed.id(),
                    seed.displayName(),
                    seed.category(),
                    normalizeCrowdLevel(level),
                    message,
                    minPeople,
                    maxPeople,
                    seed.latitude(),
                    seed.longitude(),
                    seed.address(),
                    "SEOUL_CITYDATA",
                    areaName != null && !areaName.isBlank() ? areaName : seed.seoulAreaName(),
                    updateTime
            );
        } catch (Exception error) {
            log.warn("서울 실시간 도시데이터 XML 파싱 실패: {}", seed.seoulAreaName());
            return null;
        }
    }

    private boolean cityDataXmlLooksLikeKnownArea(String xml) {
        return xml != null && (xml.contains("<AREA_NM>") || xml.contains("<AREA_CONGEST_LVL>"));
    }

    private HotplaceItem fallback(HotplaceSeed seed) {
        return new HotplaceItem(
                seed.id(),
                seed.displayName(),
                seed.category(),
                "UNKNOWN",
                "실시간 혼잡도 키가 없거나 해당 장소 응답을 받을 수 없어 후보 장소만 표시합니다.",
                null,
                null,
                seed.latitude(),
                seed.longitude(),
                seed.address(),
                "STATIC_SEED",
                seed.seoulAreaName(),
                null
        );
    }

    private boolean matches(HotplaceSeed seed, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalized = keyword.toLowerCase(Locale.ROOT).trim();
        if (HOT_NOW_KEYWORDS.contains(normalized)) {
            return true;
        }

        String compactQuery = compactSearchText(normalized);
        List<String> seedTerms = searchTerms(seed);
        boolean matchesSpecificPlace = seedTerms.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalized) || queryContainsTerm(compactQuery, value));
        if (matchesSpecificPlace) {
            return true;
        }
        if (queryHasSpecificPlace(compactQuery)) {
            return false;
        }

        return isCitywideHotQuery(compactQuery) || isCitywideCrowdQuery(compactQuery);
    }

    private boolean isCitywideHotQuery(String compactQuery) {
        return CITYWIDE_HOT_QUERY_FRAGMENTS.stream().anyMatch(compactQuery::contains);
    }

    private boolean isCitywideCrowdQuery(String compactQuery) {
        return CROWD_QUERY_FRAGMENTS.stream().anyMatch(compactQuery::contains)
                && searchTermsForAllSeeds().stream()
                .map(this::compactSearchText)
                .noneMatch(compactQuery::contains);
    }

    private boolean queryHasSpecificPlace(String compactQuery) {
        return searchTermsForAllSeeds().stream()
                .anyMatch(term -> queryContainsTerm(compactQuery, term));
    }

    private boolean queryContainsTerm(String compactQuery, String term) {
        String compactTerm = compactSearchText(term);
        return compactTerm.length() >= 2 && compactQuery.contains(compactTerm);
    }

    private List<String> searchTermsForAllSeeds() {
        return SEOUL_SEEDS.stream()
                .flatMap(seed -> searchTerms(seed).stream())
                .toList();
    }

    private List<String> searchTerms(HotplaceSeed seed) {
        List<String> terms = new ArrayList<>(List.of(
                seed.id(),
                seed.displayName(),
                seed.seoulAreaName(),
                seed.address(),
                seed.category()
        ));

        switch (seed.id()) {
            case "lotte-world" -> terms.addAll(List.of("롯데월드", "잠실", "lotte world", "jamsil"));
            case "lotte-tower-lake" -> terms.addAll(List.of("롯데타워", "월드몰", "롯데몰", "석촌호수", "잠실", "lotte tower"));
            case "hongdae" -> terms.addAll(List.of("홍대", "홍대입구", "hongdae"));
            case "seongsu" -> terms.addAll(List.of("성수", "성수동", "seongsu"));
            case "yeouido" -> terms.addAll(List.of("여의도", "한강공원", "yeouido"));
            case "gangnam-station" -> terms.addAll(List.of("강남", "강남역", "gangnam"));
            case "myeongdong" -> terms.addAll(List.of("명동", "myeongdong"));
            case "itaewon" -> terms.addAll(List.of("이태원", "itaewon"));
            case "dongdaemun" -> terms.addAll(List.of("동대문", "ddp", "dongdaemun"));
            case "gangnam-mice" -> terms.addAll(List.of("코엑스", "무역센터", "coex", "강남"));
            case "apgujeong-rodeo" -> terms.addAll(List.of("압구정", "압구정로데오", "apgujeong"));
            case "garosu-gil" -> terms.addAll(List.of("가로수길", "신사동", "sinsa"));
            case "yeonnam" -> terms.addAll(List.of("연남", "연남동", "yeonnam"));
            case "ikseon" -> terms.addAll(List.of("익선", "익선동", "ikseon"));
            case "insadong" -> terms.addAll(List.of("인사동", "insadong"));
            case "bukchon" -> terms.addAll(List.of("북촌", "한옥마을", "bukchon"));
            case "songridan" -> terms.addAll(List.of("송리단길", "호수단길", "송파", "songridan"));
            case "ddp" -> terms.addAll(List.of("DDP", "동대문디자인플라자"));
            case "gwangjang-market" -> terms.addAll(List.of("광장시장", "광장", "gwangjang"));
            case "times-square" -> terms.addAll(List.of("타임스퀘어", "영등포", "times square"));
            default -> {
            }
        }

        return terms;
    }

    private String compactSearchText(String value) {
        return SEARCH_NORMALIZE_PATTERN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private int crowdRank(HotplaceItem item) {
        return switch (item.crowdLevel()) {
            case "붐빔" -> 4;
            case "약간 붐빔" -> 3;
            case "보통" -> 2;
            case "여유" -> 1;
            default -> 0;
        };
    }

    private int fallbackHotRank(HotplaceItem item) {
        return switch (item.id()) {
            case "hongdae", "seongsu", "gangnam-station", "lotte-world", "myeongdong" -> 40;
            case "yeouido", "gangnam-mice", "ddp", "dongdaemun", "gwangjang-market" -> 30;
            case "yeonnam", "ikseon", "apgujeong-rodeo", "garosu-gil", "songridan" -> 20;
            default -> 10;
        };
    }

    private int estimatedPeopleRank(HotplaceItem item) {
        if (item.estimatedMaxPeople() != null) {
            return item.estimatedMaxPeople();
        }
        if (item.estimatedMinPeople() != null) {
            return item.estimatedMinPeople();
        }
        return 0;
    }

    private String normalizeCrowdLevel(String value) {
        if (value == null) {
            return "UNKNOWN";
        }

        String trimmed = value.trim();
        String normalizedCode = trimmed
                .toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        return switch (normalizedCode) {
            case "VERY_CROWDED", "CROWDED", "BUSY", "HIGH", "LEVEL_4", "LV4", "4" -> "붐빔";
            case "SLIGHTLY_CROWDED", "MODERATE_BUSY", "MEDIUM_HIGH", "LEVEL_3", "LV3", "3" -> "약간 붐빔";
            case "NORMAL", "MODERATE", "MEDIUM", "LEVEL_2", "LV2", "2" -> "보통";
            case "RELAXED", "LOW", "QUIET", "FREE", "LEVEL_1", "LV1", "1" -> "여유";
            default -> switch (trimmed) {
                case "붐빔", "약간 붐빔", "보통", "여유" -> trimmed;
                default -> trimmed;
            };
        };
    }

    private String firstText(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> objectValue(Object value) {
        return value instanceof Map<?, ?> map ? Optional.of((Map<String, Object>) map) : Optional.empty();
    }

    private List<Map<String, Object>> telecomCrowdDataCandidates(Map<String, Object> payload) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        Queue<Object> queue = new ArrayDeque<>();
        queue.add(payload);

        while (!queue.isEmpty() && candidates.size() < 80) {
            Object current = queue.poll();
            if (current instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) map;
                candidates.add(object);
                for (Object key : List.of("data", "result", "body", "items", "item", "features", "properties", "crowd", "congestion")) {
                    Object child = object.get(key);
                    if (child instanceof Map<?, ?> || child instanceof List<?>) {
                        queue.add(child);
                    }
                }
                continue;
            }
            if (current instanceof List<?> list) {
                list.stream()
                        .filter(item -> item instanceof Map<?, ?> || item instanceof List<?>)
                        .limit(20)
                        .forEach(queue::add);
            }
        }

        return candidates;
    }

    private boolean hasCrowdSignal(Map<String, Object> data) {
        return firstString(data, List.of("crowdLevel", "congestionLevel", "congestion", "level")) != null
                || firstInteger(data, List.of("estimatedMinPeople", "minPeople", "populationMin", "peopleMin")) != null
                || firstInteger(data, List.of("estimatedMaxPeople", "maxPeople", "populationMax", "peopleMax")) != null
                || !firstPopulationRange(data, List.of("estimatedPeople", "population", "populationRange", "people", "peopleRange", "visitorCount")).isEmpty();
    }

    private String firstString(Map<String, Object> data, List<String> keys) {
        return keys.stream()
                .map(data::get)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Integer firstInteger(Map<String, Object> data, List<String> keys) {
        return keys.stream()
                .map(data::get)
                .map(this::parseIntegerValue)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private PopulationRange firstPopulationRange(Map<String, Object> data, List<String> keys) {
        return keys.stream()
                .map(data::get)
                .map(this::parsePopulationRange)
                .filter(range -> !range.isEmpty())
                .findFirst()
                .orElse(PopulationRange.empty());
    }

    private Optional<Integer> parseIntegerValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        if (value == null) {
            return Optional.empty();
        }

        Matcher matcher = INTEGER_PATTERN.matcher(String.valueOf(value));
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group().replace(",", "")));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private PopulationRange parsePopulationRange(Object value) {
        if (value instanceof Number number) {
            int count = number.intValue();
            return new PopulationRange(count, count);
        }
        if (value == null) {
            return PopulationRange.empty();
        }

        Matcher matcher = INTEGER_PATTERN.matcher(String.valueOf(value));
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find() && numbers.size() < 2) {
            try {
                numbers.add(Integer.parseInt(matcher.group().replace(",", "")));
            } catch (NumberFormatException ignored) {
                // Keep scanning for another usable integer.
            }
        }
        if (numbers.isEmpty()) {
            return PopulationRange.empty();
        }
        if (numbers.size() == 1) {
            return new PopulationRange(numbers.getFirst(), numbers.getFirst());
        }
        return new PopulationRange(Math.min(numbers.get(0), numbers.get(1)), Math.max(numbers.get(0), numbers.get(1)));
    }

    private record HotplaceSeed(
            String id,
            String seoulAreaCode,
            String seoulAreaName,
            String displayName,
            String category,
            Double latitude,
            Double longitude,
            String address,
            List<String> cityDataQueries
    ) {
        HotplaceSeed(
                String id,
                String seoulAreaCode,
                String seoulAreaName,
                String displayName,
                String category,
                Double latitude,
                Double longitude,
                String address
        ) {
            this(id, seoulAreaCode, seoulAreaName, displayName, category, latitude, longitude, address, defaultCityDataQueries(seoulAreaCode, seoulAreaName));
        }

        private static List<String> defaultCityDataQueries(String seoulAreaCode, String seoulAreaName) {
            if (seoulAreaCode == null || seoulAreaCode.isBlank()) {
                return List.of(seoulAreaName);
            }
            return List.of(seoulAreaCode, seoulAreaName);
        }
    }

    private record CachedHotplace(HotplaceItem item, Instant expiresAt) {
    }

    private record ProviderAttempt(Instant lastSuccessAt, Instant lastFailureAt, String detail) {
    }

    private record PopulationRange(Integer minPeople, Integer maxPeople) {
        static PopulationRange empty() {
            return new PopulationRange(null, null);
        }

        boolean isEmpty() {
            return minPeople == null && maxPeople == null;
        }
    }

    private interface CrowdSignalProvider {
        Optional<HotplaceItem> fetch(HotplaceSeed seed);
    }

    private class SeoulCityDataCrowdSignalProvider implements CrowdSignalProvider {
        @Override
        public Optional<HotplaceItem> fetch(HotplaceSeed seed) {
            return fetchSeoulCityData(seed);
        }
    }

    private class TelecomCrowdSignalProvider implements CrowdSignalProvider {
        @Override
        public Optional<HotplaceItem> fetch(HotplaceSeed seed) {
            return fetchTelecomCrowd(seed);
        }
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
}
