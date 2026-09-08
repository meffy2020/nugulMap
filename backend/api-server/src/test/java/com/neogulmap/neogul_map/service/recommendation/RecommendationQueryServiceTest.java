package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.recommendation.CandidateFreshness;
import com.neogulmap.neogul_map.dto.recommendation.CandidateSearchResult;
import com.neogulmap.neogul_map.dto.recommendation.KakaoPlaceCategory;
import com.neogulmap.neogul_map.dto.recommendation.PlaceCandidate;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.PlaceSearchArea;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationDataStatus;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationIntent;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationPersonalizationMode;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationQueryServiceTest {

    private final KakaoLocalPlaceClient kakaoClient = mock(KakaoLocalPlaceClient.class);
    private final RecommendationDataService dataService = mock(RecommendationDataService.class);
    private final RecommendationQueryService service = new RecommendationQueryService(
            kakaoClient,
            new ContextRecommendationRanker(),
            dataService,
            Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC),
            () -> "req-fixed"
    );

    @Test
    void guestCafeRecommendationUsesContextWithoutReadingUserData() {
        PlaceCandidate nearbyCafe = candidate("cafe-1", "골목 카페", "카페", 420, "CE7");
        when(kakaoClient.searchCategory(
                eq(KakaoPlaceCategory.CE7),
                any(PlaceSearchArea.CenterRadius.class),
                eq(1),
                eq(15)
        )).thenReturn(success(nearbyCafe));

        var response = service.recommendPlaces(
                null,
                RecommendationIntent.CAFE,
                37.5446,
                127.0557,
                3_000,
                8
        );

        assertThat(response.requestId()).isEqualTo("req-fixed");
        assertThat(response.modelVersion()).isEqualTo(RecommendationQueryService.MODEL_VERSION);
        assertThat(response.dataStatus()).isEqualTo(RecommendationDataStatus.READY);
        assertThat(response.personalizationMode()).isEqualTo(RecommendationPersonalizationMode.CONTEXT_ONLY);
        assertThat(response.items()).extracting(item -> item.id()).containsExactly("cafe-1");
        assertThat(response.items().getFirst().distanceMeters()).isEqualTo(420);
        verify(dataService, never()).getPreference(any());
    }

    @Test
    void optedInUserPreferenceAndNotInterestedHistoryChangeRanking() {
        User user = User.builder().id(7L).build();
        when(dataService.getPreference(user)).thenReturn(new UserPreferenceResponse(
                true,
                Set.of("디저트"),
                Set.of(),
                null,
                2_000,
                null
        ));
        when(dataService.getSavedItems(user)).thenReturn(List.of());
        when(dataService.getRecentExcludedItemIds(user, 100)).thenReturn(Set.of("kakao_local:cafe-2"));
        when(kakaoClient.searchCategory(
                eq(KakaoPlaceCategory.CE7),
                any(PlaceSearchArea.CenterRadius.class),
                eq(1),
                eq(15)
        )).thenReturn(CandidateSearchResult.success(
                PlaceProvider.KAKAO_LOCAL,
                List.of(
                        candidate("cafe-1", "디저트 카페", "카페 > 디저트카페", 600, "CE7", "디저트"),
                        candidate("cafe-2", "제외할 카페", "카페", 100, "CE7")
                ),
                1,
                15,
                true
        ));

        var response = service.recommendPlaces(
                user,
                RecommendationIntent.CAFE,
                37.5446,
                127.0557,
                5_000,
                8
        );

        assertThat(response.personalizationMode()).isEqualTo(RecommendationPersonalizationMode.PERSONALIZED);
        assertThat(response.items()).extracting(item -> item.id()).containsExactly("cafe-1");
        assertThat(response.items().getFirst().reasonCodes())
                .contains(com.neogulmap.neogul_map.dto.recommendation.RecommendationReasonCode.PREFERRED_TAG_MATCH);
    }

    @Test
    void unconfiguredProviderReturnsHonestEmptyState() {
        when(kakaoClient.searchCategory(
                eq(KakaoPlaceCategory.FD6),
                any(PlaceSearchArea.CenterRadius.class),
                eq(1),
                eq(15)
        )).thenReturn(CandidateSearchResult.unconfigured(PlaceProvider.KAKAO_LOCAL, 1, 15));

        var response = service.recommendPlaces(
                null,
                RecommendationIntent.RESTAURANT,
                37.5,
                127.0,
                3_000,
                8
        );

        assertThat(response.dataStatus()).isEqualTo(RecommendationDataStatus.PROVIDER_NOT_CONFIGURED);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void upstreamRejectionReturnsUnavailableWhenNoCategorySucceeded() {
        when(kakaoClient.searchCategory(
                eq(KakaoPlaceCategory.CE7),
                any(PlaceSearchArea.CenterRadius.class),
                eq(1),
                eq(15)
        )).thenReturn(CandidateSearchResult.upstreamError(PlaceProvider.KAKAO_LOCAL, 1, 15));

        var response = service.recommendPlaces(
                null,
                RecommendationIntent.CAFE,
                37.5,
                127.0,
                3_000,
                8
        );

        assertThat(response.dataStatus()).isEqualTo(RecommendationDataStatus.PROVIDER_UNAVAILABLE);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void dateRecommendationIsPartialWhenBulkheadRejectsSomeCategoryCalls() {
        for (KakaoPlaceCategory category : KakaoPlaceCategory.values()) {
            CandidateSearchResult result = category == KakaoPlaceCategory.CE7
                    ? CandidateSearchResult.success(PlaceProvider.KAKAO_LOCAL, List.of(), 1, 15, true)
                    : CandidateSearchResult.upstreamError(PlaceProvider.KAKAO_LOCAL, 1, 15);
            when(kakaoClient.searchCategory(
                    eq(category),
                    any(PlaceSearchArea.CenterRadius.class),
                    eq(1),
                    eq(15)
            )).thenReturn(result);
        }

        var response = service.recommendPlaces(
                null,
                RecommendationIntent.DATE,
                37.5,
                127.0,
                3_000,
                8
        );

        assertThat(response.dataStatus()).isEqualTo(RecommendationDataStatus.PARTIAL);
    }

    @Test
    void dateIntentCollectsAllFourSupportedPlaceCategories() {
        for (KakaoPlaceCategory category : KakaoPlaceCategory.values()) {
            when(kakaoClient.searchCategory(
                    eq(category),
                    any(PlaceSearchArea.CenterRadius.class),
                    eq(1),
                    eq(15)
            )).thenReturn(CandidateSearchResult.success(
                    PlaceProvider.KAKAO_LOCAL,
                    List.of(),
                    1,
                    15,
                    true
            ));
        }

        var response = service.recommendPlaces(
                null,
                RecommendationIntent.DATE,
                37.5,
                127.0,
                3_000,
                8
        );

        assertThat(response.dataStatus()).isEqualTo(RecommendationDataStatus.READY);
        for (KakaoPlaceCategory category : KakaoPlaceCategory.values()) {
            verify(kakaoClient).searchCategory(
                    eq(category),
                    any(PlaceSearchArea.CenterRadius.class),
                    eq(1),
                    eq(15)
            );
        }
    }

    private CandidateSearchResult success(PlaceCandidate candidate) {
        return CandidateSearchResult.success(
                PlaceProvider.KAKAO_LOCAL,
                List.of(candidate),
                1,
                15,
                true
        );
    }

    private PlaceCandidate candidate(
            String id,
            String name,
            String category,
            int distanceMeters,
            String... tags
    ) {
        return new PlaceCandidate(
                id,
                name,
                category,
                "서울 성동구",
                37.5446,
                127.0557,
                distanceMeters,
                "https://place.map.kakao.com/" + id,
                PlaceProvider.KAKAO_LOCAL,
                CandidateFreshness.LIVE,
                Set.of(tags)
        );
    }
}
