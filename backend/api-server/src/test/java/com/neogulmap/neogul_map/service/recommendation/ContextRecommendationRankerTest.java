package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.dto.recommendation.CandidateFreshness;
import com.neogulmap.neogul_map.dto.recommendation.PlaceCandidate;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationContext;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationIntent;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationReasonCode;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextRecommendationRankerTest {

    private final ContextRecommendationRanker ranker = new ContextRecommendationRanker();

    @Test
    void hardFiltersIntentDistanceDislikedTagsAndBothRecentExclusionKeyFormats() {
        RecommendationContext context = new RecommendationContext(
                RecommendationIntent.CAFE,
                Set.of(),
                Set.of("시끄러운"),
                1_000,
                Set.of("recent-raw", "kakao_local:recent-qualified"),
                Set.of(),
                10
        );

        RecommendationResult result = ranker.rank(List.of(
                cafe("keep", 500, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of("조용한")),
                cafe("too-far", 1_001, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of()),
                cafe("disliked", 400, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of("시끄러운")),
                cafe("recent-raw", 300, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of()),
                cafe("recent-qualified", 200, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of()),
                restaurant("wrong-intent", 100),
                cafe("unknown-distance", null, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of())
        ), context);

        assertThat(result.eligibleCandidateCount()).isEqualTo(1);
        assertThat(result.items()).extracting(item -> item.candidate().id()).containsExactly("keep");
    }

    @Test
    void rankingReflectsPreferenceFreshnessProviderAndDistanceWithOnlyGroundedReasons() {
        RecommendationContext context = new RecommendationContext(
                RecommendationIntent.CAFE,
                Set.of("조용한"),
                Set.of(),
                10_000,
                Set.of(),
                Set.of("kakao_local:official-near"),
                5
        );

        RecommendationResult result = ranker.rank(List.of(
                cafe("community-old", 6_000, PlaceProvider.USER_SUBMITTED, CandidateFreshness.STALE, Set.of()),
                cafe("official-near", 500, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of("조용한"))
        ), context);

        assertThat(result.items()).extracting(item -> item.candidate().id())
                .containsExactly("official-near", "community-old");
        assertThat(result.items().getFirst().reasonCodes()).containsExactly(
                RecommendationReasonCode.CAFE_INTENT_MATCH,
                RecommendationReasonCode.PREFERRED_TAG_MATCH,
                RecommendationReasonCode.POSITIVE_HISTORY_MATCH,
                RecommendationReasonCode.FRESH_CANDIDATE,
                RecommendationReasonCode.TRUSTED_PROVIDER,
                RecommendationReasonCode.WITHIN_ONE_KILOMETER
        );
        assertThat(result.items().getLast().reasonCodes()).containsExactly(
                RecommendationReasonCode.CAFE_INTENT_MATCH
        );
        assertThat(result.items().getFirst().score()).isGreaterThan(result.items().getLast().score());
    }

    @Test
    void dateRankingPromotesAnotherCategoryBeforeARepeatedHighScoreCategory() {
        RecommendationContext context = new RecommendationContext(
                RecommendationIntent.DATE,
                Set.of(),
                Set.of(),
                20_000,
                Set.of(),
                Set.of(),
                3
        );
        PlaceCandidate restaurantOne = restaurant("restaurant-1", 100);
        PlaceCandidate restaurantTwo = restaurant("restaurant-2", 110);
        PlaceCandidate culture = candidate(
                "culture-1",
                "문화,예술 > 문화시설 > 전시관",
                2_000,
                PlaceProvider.KAKAO_LOCAL,
                CandidateFreshness.LIVE,
                Set.of("CT1", "문화시설", "전시관")
        );

        RecommendationResult result = ranker.rank(
                List.of(restaurantTwo, culture, restaurantOne),
                context
        );

        assertThat(result.items()).extracting(item -> item.candidate().id())
                .containsExactly("restaurant-1", "culture-1", "restaurant-2");
        assertThat(result.items().get(1).reasonCodes()).contains(RecommendationReasonCode.DIVERSE_CATEGORY);
        assertThat(result.items().get(2).reasonCodes()).doesNotContain(RecommendationReasonCode.DIVERSE_CATEGORY);
    }

    @Test
    void tieBreakingAndProviderIdDeduplicationAreDeterministic() {
        RecommendationContext context = new RecommendationContext(
                RecommendationIntent.CAFE,
                Set.of(),
                Set.of(),
                null,
                Set.of(),
                Set.of(),
                10
        );
        PlaceCandidate a = cafe("a", 500, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of());
        PlaceCandidate duplicateA = cafe("a", 500, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of());
        PlaceCandidate b = cafe("b", 500, PlaceProvider.KAKAO_LOCAL, CandidateFreshness.LIVE, Set.of());

        RecommendationResult first = ranker.rank(List.of(b, duplicateA, a), context);
        RecommendationResult second = ranker.rank(List.of(a, b, duplicateA), context);

        assertThat(first.eligibleCandidateCount()).isEqualTo(2);
        assertThat(first.items()).extracting(item -> item.candidate().id()).containsExactly("a", "b");
        assertThat(second).isEqualTo(first);
    }

    private static PlaceCandidate cafe(
            String id,
            Integer distance,
            PlaceProvider provider,
            CandidateFreshness freshness,
            Set<String> extraTags
    ) {
        return candidate(
                id,
                "음식점 > 카페 > 커피전문점",
                distance,
                provider,
                freshness,
                union(Set.of("CE7", "카페", "커피전문점"), extraTags)
        );
    }

    private static PlaceCandidate restaurant(String id, Integer distance) {
        return candidate(
                id,
                "음식점 > 한식",
                distance,
                PlaceProvider.KAKAO_LOCAL,
                CandidateFreshness.LIVE,
                Set.of("FD6", "음식점", "한식")
        );
    }

    private static PlaceCandidate candidate(
            String id,
            String category,
            Integer distance,
            PlaceProvider provider,
            CandidateFreshness freshness,
            Set<String> tags
    ) {
        return new PlaceCandidate(
                id,
                "장소 " + id,
                category,
                "서울 성동구",
                37.55,
                127.05,
                distance,
                "https://example.com/" + id,
                provider,
                freshness,
                tags
        );
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(left);
        merged.addAll(right);
        return merged;
    }
}
