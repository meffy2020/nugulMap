package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.recommendation.CandidateSearchResult;
import com.neogulmap.neogul_map.dto.recommendation.KakaoPlaceCategory;
import com.neogulmap.neogul_map.dto.recommendation.PlaceCandidate;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.PlaceSearchArea;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationContext;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationDataStatus;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationIntent;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationPersonalizationMode;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationPlaceResponse;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationResult;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceResponse;
import com.neogulmap.neogul_map.dto.recommendation.UserSavedItemResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Service
public class RecommendationQueryService {

    public static final String MODEL_VERSION = "pero-context-v1";
    public static final int MIN_RADIUS_METERS = 100;
    public static final int MAX_RADIUS_METERS = 20_000;
    private static final int PROVIDER_PAGE_SIZE = 15;
    private static final int EXCLUDED_ITEM_LIMIT = 100;

    private final KakaoLocalPlaceClient kakaoLocalPlaceClient;
    private final ContextRecommendationRanker ranker;
    private final RecommendationDataService recommendationDataService;
    private final Clock clock;
    private final Supplier<String> requestIdSupplier;

    @Autowired
    public RecommendationQueryService(
            KakaoLocalPlaceClient kakaoLocalPlaceClient,
            ContextRecommendationRanker ranker,
            RecommendationDataService recommendationDataService
    ) {
        this(
                kakaoLocalPlaceClient,
                ranker,
                recommendationDataService,
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString()
        );
    }

    RecommendationQueryService(
            KakaoLocalPlaceClient kakaoLocalPlaceClient,
            ContextRecommendationRanker ranker,
            RecommendationDataService recommendationDataService,
            Clock clock,
            Supplier<String> requestIdSupplier
    ) {
        this.kakaoLocalPlaceClient = kakaoLocalPlaceClient;
        this.ranker = ranker;
        this.recommendationDataService = recommendationDataService;
        this.clock = clock;
        this.requestIdSupplier = requestIdSupplier;
    }

    public RecommendationPlaceResponse recommendPlaces(
            User user,
            RecommendationIntent intent,
            double latitude,
            double longitude,
            int radiusMeters,
            int limit
    ) {
        requireCoordinates(latitude, longitude);
        if (intent == null) {
            throw new IllegalArgumentException("추천 목적이 필요합니다.");
        }
        if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
            throw new IllegalArgumentException("추천 반경은 100m 이상 20km 이하여야 합니다.");
        }

        PlaceSearchArea area = new PlaceSearchArea.CenterRadius(latitude, longitude, radiusMeters);
        List<CandidateSearchResult> providerResults = fetchProviderResults(intent, area);

        RecommendationPersonalizationMode mode = RecommendationPersonalizationMode.CONTEXT_ONLY;
        Set<String> preferredTags = Set.of();
        Set<String> dislikedTags = Set.of();
        Set<String> recentExcludedIds = Set.of();
        Set<String> positiveHistoryItemIds = Set.of();
        Integer maximumDistanceMeters = radiusMeters;

        if (user != null && user.getId() != null) {
            UserPreferenceResponse preference = recommendationDataService.getPreference(user);
            if (preference.personalizationEnabled()) {
                mode = RecommendationPersonalizationMode.PERSONALIZED;
                preferredTags = personalizedTags(preference, recommendationDataService.getSavedItems(user));
                dislikedTags = preference.dislikedTags();
                recentExcludedIds = recommendationDataService.getRecentExcludedItemIds(
                        user,
                        EXCLUDED_ITEM_LIMIT
                );
                positiveHistoryItemIds = recommendationDataService.getRecentPositiveItemIds(
                        user,
                        EXCLUDED_ITEM_LIMIT
                );
                if (preference.maxDistanceMeters() != null) {
                    maximumDistanceMeters = Math.min(radiusMeters, preference.maxDistanceMeters());
                }
            }
        }

        List<PlaceCandidate> candidates = providerResults.stream()
                .filter(result -> result.status() == CandidateSearchResult.Status.SUCCESS)
                .flatMap(result -> result.candidates().stream())
                .toList();
        RecommendationResult ranked = ranker.rank(
                candidates,
                new RecommendationContext(
                        intent,
                        preferredTags,
                        dislikedTags,
                        maximumDistanceMeters,
                        recentExcludedIds,
                        positiveHistoryItemIds,
                        limit
                )
        );

        return new RecommendationPlaceResponse(
                requestIdSupplier.get(),
                MODEL_VERSION,
                aggregateStatus(providerResults),
                mode,
                intent,
                ranked.eligibleCandidateCount(),
                Instant.now(clock),
                ranked.items().stream().map(RecommendationPlaceResponse.Item::from).toList()
        );
    }

    private Set<String> personalizedTags(
            UserPreferenceResponse preference,
            List<UserSavedItemResponse> savedItems
    ) {
        LinkedHashSet<String> tags = new LinkedHashSet<>(preference.preferredTags());
        if (savedItems != null) {
            savedItems.stream()
                    .map(UserSavedItemResponse::category)
                    .filter(category -> category != null && !category.isBlank())
                    .limit(20)
                    .forEach(category -> {
                        tags.add(category);
                        for (String part : category.split(">")) {
                            if (!part.isBlank()) {
                                tags.add(part.trim());
                            }
                        }
                    });
        }
        return Set.copyOf(tags);
    }

    private List<KakaoPlaceCategory> categoriesFor(RecommendationIntent intent) {
        return switch (intent) {
            case CAFE -> List.of(KakaoPlaceCategory.CE7);
            case RESTAURANT -> List.of(KakaoPlaceCategory.FD6);
            case DATE -> List.of(
                    KakaoPlaceCategory.CT1,
                    KakaoPlaceCategory.AT4,
                    KakaoPlaceCategory.FD6,
                    KakaoPlaceCategory.CE7
            );
        };
    }

    private List<CandidateSearchResult> fetchProviderResults(
            RecommendationIntent intent,
            PlaceSearchArea area
    ) {
        List<KakaoPlaceCategory> categories = categoriesFor(intent);
        if (categories.size() == 1) {
            return List.of(fetchCategory(categories.getFirst(), area));
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return categories.stream()
                    .map(category -> CompletableFuture
                            .supplyAsync(() -> fetchCategory(category, area), executor)
                            .exceptionally(ignored -> CandidateSearchResult.upstreamError(
                                    PlaceProvider.KAKAO_LOCAL,
                                    1,
                                    PROVIDER_PAGE_SIZE
                            )))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
    }

    private CandidateSearchResult fetchCategory(
            KakaoPlaceCategory category,
            PlaceSearchArea area
    ) {
        return kakaoLocalPlaceClient.searchCategory(
                category,
                area,
                1,
                PROVIDER_PAGE_SIZE
        );
    }

    private RecommendationDataStatus aggregateStatus(List<CandidateSearchResult> results) {
        boolean anySuccess = results.stream()
                .anyMatch(result -> result.status() == CandidateSearchResult.Status.SUCCESS);
        boolean anyFailure = results.stream()
                .anyMatch(result -> result.status() == CandidateSearchResult.Status.UPSTREAM_ERROR);
        boolean allUnconfigured = results.stream()
                .allMatch(result -> result.status() == CandidateSearchResult.Status.UNCONFIGURED);

        if (allUnconfigured) {
            return RecommendationDataStatus.PROVIDER_NOT_CONFIGURED;
        }
        if (!anySuccess) {
            return RecommendationDataStatus.PROVIDER_UNAVAILABLE;
        }
        return anyFailure ? RecommendationDataStatus.PARTIAL : RecommendationDataStatus.READY;
    }

    private void requireCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("위도 범위가 올바르지 않습니다.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("경도 범위가 올바르지 않습니다.");
        }
    }
}
