package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.recommendation.BudgetLevel;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationInteraction;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import com.neogulmap.neogul_map.domain.recommendation.UserPreference;
import com.neogulmap.neogul_map.domain.recommendation.UserSavedItem;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationInteractionRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceUpdateRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserSavedItemRequest;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.repository.recommendation.RecommendationInteractionRepository;
import com.neogulmap.neogul_map.repository.recommendation.UserPreferenceRepository;
import com.neogulmap.neogul_map.repository.recommendation.UserSavedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private UserSavedItemRepository userSavedItemRepository;
    @Mock private RecommendationInteractionRepository recommendationInteractionRepository;
    @Mock private UserRepository userRepository;

    private RecommendationDataService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RecommendationDataService(
                userPreferenceRepository,
                userSavedItemRepository,
                recommendationInteractionRepository,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        user = User.builder().id(7L).build();
        lenient().when(userRepository.findForUpdateById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void preferenceUpdateNormalizesTagsAndDefaultsToOptInOnly() {
        when(userPreferenceRepository.findByUser_Id(7L)).thenReturn(Optional.empty());
        when(userPreferenceRepository.save(any(UserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updatePreference(user, new UserPreferenceUpdateRequest(
                true,
                new LinkedHashSet<>(List.of(" 조용한 ", "DESSERT")),
                Set.of("웨이팅"),
                BudgetLevel.MEDIUM,
                3_000
        ));

        assertThat(response.personalizationEnabled()).isTrue();
        assertThat(response.preferredTags()).containsExactlyInAnyOrder("조용한", "dessert");
        assertThat(response.dislikedTags()).containsExactly("웨이팅");
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    @Test
    void saveItemNormalizesProviderAndIsIdempotentByProviderKey() {
        when(userSavedItemRepository.findByUser_IdAndItemTypeAndProviderAndItemId(
                7L, RecommendationItemType.PLACE, "kakao", "123"))
                .thenReturn(Optional.empty());
        when(userSavedItemRepository.countByUser_Id(7L)).thenReturn(0L);
        when(userSavedItemRepository.save(any(UserSavedItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.saveItem(user, new UserSavedItemRequest(
                RecommendationItemType.PLACE,
                " KAKAO ",
                "123",
                "성수 카페",
                "카페",
                BigDecimal.valueOf(37.5445),
                BigDecimal.valueOf(127.0560),
                "https://place.map.kakao.com/123"
        ));

        assertThat(response.provider()).isEqualTo("kakao");
        assertThat(response.itemId()).isEqualTo("123");
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        verify(userSavedItemRepository).findByUser_IdAndItemTypeAndProviderAndItemId(
                7L, RecommendationItemType.PLACE, "kakao", "123");
        verify(userSavedItemRepository).countByUser_Id(7L);
    }

    @Test
    void existingSavedItemCanBeRefreshedAtUserLimit() {
        UserSavedItem existing = savedItem(88L, "123", "이전 이름");
        when(userSavedItemRepository.findByUser_IdAndItemTypeAndProviderAndItemId(
                7L, RecommendationItemType.PLACE, "kakao", "123"))
                .thenReturn(Optional.of(existing));
        when(userSavedItemRepository.save(existing)).thenReturn(existing);

        var response = service.saveItem(user, savedItemRequest("123", "새 이름"));

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.title()).isEqualTo("새 이름");
        verify(userSavedItemRepository, never()).countByUser_Id(7L);
    }

    @Test
    void newSavedItemIsRejectedAtUserLimit() {
        when(userSavedItemRepository.findByUser_IdAndItemTypeAndProviderAndItemId(
                7L, RecommendationItemType.PLACE, "kakao", "501"))
                .thenReturn(Optional.empty());
        when(userSavedItemRepository.countByUser_Id(7L))
                .thenReturn((long) RecommendationDataService.MAX_SAVED_ITEMS_PER_USER);

        assertThatThrownBy(() -> service.saveItem(user, savedItemRequest("501", "한도 초과")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("최대 500개");
        verify(userSavedItemRepository, never()).save(any(UserSavedItem.class));
    }

    @Test
    void interactionUsesAuthenticatedUserAndIgnoresClientLocationAndTime() {
        when(userPreferenceRepository.findByUser_Id(7L)).thenReturn(Optional.of(enabledPreference()));
        when(recommendationInteractionRepository.countByUser_Id(7L)).thenReturn(0L);
        when(recommendationInteractionRepository.save(any(RecommendationInteraction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.recordInteraction(user, new RecommendationInteractionRequest(
                "request-1",
                RecommendationItemType.PLACE,
                "KAKAO",
                "123",
                RecommendationEventType.ROUTE,
                "CAFE",
                "37.5446,127.0557",
                "context-v1",
                LocalDateTime.of(2099, 1, 1, 0, 0)
        ));

        assertThat(response.provider()).isEqualTo("kakao");
        assertThat(response.eventType()).isEqualTo(RecommendationEventType.ROUTE);
        assertThat(response.contextRegion()).isNull();
        assertThat(response.occurredAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    @Test
    void duplicateInteractionReturnsExistingResponseWithoutUsingCapacity() {
        RecommendationInteraction existing = interaction(91L, "kakao", "123", RecommendationEventType.ROUTE);
        when(userPreferenceRepository.findByUser_Id(7L)).thenReturn(Optional.of(enabledPreference()));
        when(recommendationInteractionRepository
                .findByUser_IdAndRequestIdAndItemTypeAndProviderAndItemIdAndEventType(
                        7L,
                        "request-1",
                        RecommendationItemType.PLACE,
                        "kakao",
                        "123",
                        RecommendationEventType.ROUTE
                ))
                .thenReturn(Optional.of(existing));

        var response = service.recordInteraction(user, interactionRequest("123", RecommendationEventType.ROUTE));

        assertThat(response.id()).isEqualTo(91L);
        assertThat(response.occurredAt()).isEqualTo(existing.getOccurredAt());
        verify(recommendationInteractionRepository, never()).countByUser_Id(7L);
        verify(recommendationInteractionRepository, never()).save(any(RecommendationInteraction.class));
    }

    @Test
    void newInteractionIsRejectedAtUserLimit() {
        when(userPreferenceRepository.findByUser_Id(7L)).thenReturn(Optional.of(enabledPreference()));
        when(recommendationInteractionRepository.countByUser_Id(7L))
                .thenReturn((long) RecommendationDataService.MAX_INTERACTIONS_PER_USER);

        assertThatThrownBy(() -> service.recordInteraction(
                user,
                interactionRequest("10001", RecommendationEventType.IMPRESSION)
        ))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("최대 10,000개");
        verify(recommendationInteractionRepository, never()).save(any(RecommendationInteraction.class));
    }

    @Test
    void interactionIsRejectedUntilUserOptsInToPersonalization() {
        when(userPreferenceRepository.findByUser_Id(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordInteraction(user, new RecommendationInteractionRequest(
                "request-1",
                RecommendationItemType.PLACE,
                "KAKAO_LOCAL",
                "123",
                RecommendationEventType.DETAIL_OPEN,
                "CAFE",
                "성동구",
                "pero-context-v1",
                null
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("개인화를 켠 사용자");
    }

    @Test
    void optingOutDeletesLearnedInteractionsButKeepsSavedItems() {
        when(userPreferenceRepository.findByUser_Id(7L)).thenReturn(Optional.of(enabledPreference()));
        when(userPreferenceRepository.save(any(UserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updatePreference(user, new UserPreferenceUpdateRequest(
                false,
                Set.of(),
                Set.of(),
                null,
                null
        ));

        verify(recommendationInteractionRepository).deleteByUser_Id(7L);
        org.mockito.Mockito.verifyNoInteractions(userSavedItemRepository);
    }

    @Test
    void clearRecommendationDataDeletesPreferenceSavesAndInteractions() {
        service.clearRecommendationData(user);

        verify(userRepository).findForUpdateById(7L);
        verify(recommendationInteractionRepository).deleteByUser_Id(7L);
        verify(userSavedItemRepository).deleteByUser_Id(7L);
        verify(userPreferenceRepository).deleteByUser_Id(7L);
    }

    @Test
    void recentExclusionsContainDistinctProviderScopedKeysInLatestOrder() {
        when(recommendationInteractionRepository.findByUser_IdAndEventTypeOrderByOccurredAtDesc(
                7L, RecommendationEventType.NOT_INTERESTED, PageRequest.of(0, 3)))
                .thenReturn(List.of(
                        interaction("kakao", "123"),
                        interaction("naver", "123"),
                        interaction("kakao", "123")
                ));

        assertThat(service.getRecentExcludedItemIds(user, 3))
                .containsExactly("kakao:123", "naver:123");
    }

    @Test
    void recentPositiveHistoryUsesOnlyDetailSaveAndRouteEvents() {
        Set<RecommendationEventType> positiveEvents = Set.of(
                RecommendationEventType.DETAIL_OPEN,
                RecommendationEventType.SAVE,
                RecommendationEventType.ROUTE
        );
        when(recommendationInteractionRepository.findByUser_IdAndEventTypeInOrderByOccurredAtDesc(
                7L, positiveEvents, PageRequest.of(0, 4)))
                .thenReturn(List.of(
                        interaction(null, "kakao", "route", RecommendationEventType.ROUTE),
                        interaction(null, "kakao", "detail", RecommendationEventType.DETAIL_OPEN),
                        interaction(null, "kakao", "route", RecommendationEventType.SAVE)
                ));

        assertThat(service.getRecentPositiveItemIds(user, 4))
                .containsExactly("kakao:route", "kakao:detail");
    }

    @Test
    void unauthenticatedUsersCannotPersistRecommendationData() {
        assertThatThrownBy(() -> service.recordInteraction(User.builder().build(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("로그인한 사용자");
    }

    @Test
    void preferenceRejectsOverlappingPositiveAndNegativeTags() {
        assertThatThrownBy(() -> service.updatePreference(user, new UserPreferenceUpdateRequest(
                true,
                Set.of("조용한"),
                Set.of(" 조용한 "),
                null,
                null
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("겹칠 수 없습니다");
    }

    private RecommendationInteraction interaction(String provider, String itemId) {
        return interaction(null, provider, itemId, RecommendationEventType.NOT_INTERESTED);
    }

    private RecommendationInteraction interaction(
            Long id,
            String provider,
            String itemId,
            RecommendationEventType eventType
    ) {
        return RecommendationInteraction.builder()
                .id(id)
                .user(user)
                .requestId("request-1")
                .itemType(RecommendationItemType.PLACE)
                .provider(provider)
                .itemId(itemId)
                .eventType(eventType)
                .intent("CAFE")
                .modelVersion("context-v1")
                .occurredAt(LocalDateTime.of(2026, 9, 1, 0, 0))
                .build();
    }

    private RecommendationInteractionRequest interactionRequest(
            String itemId,
            RecommendationEventType eventType
    ) {
        return new RecommendationInteractionRequest(
                "request-1",
                RecommendationItemType.PLACE,
                "KAKAO",
                itemId,
                eventType,
                "CAFE",
                null,
                "context-v1",
                null
        );
    }

    private UserSavedItem savedItem(Long id, String itemId, String title) {
        return UserSavedItem.builder()
                .id(id)
                .user(user)
                .itemType(RecommendationItemType.PLACE)
                .provider("kakao")
                .itemId(itemId)
                .title(title)
                .category("카페")
                .lat(BigDecimal.valueOf(37.5445))
                .lng(BigDecimal.valueOf(127.0560))
                .sourceUrl("https://place.map.kakao.com/" + itemId)
                .createdAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .build();
    }

    private UserSavedItemRequest savedItemRequest(String itemId, String title) {
        return new UserSavedItemRequest(
                RecommendationItemType.PLACE,
                "KAKAO",
                itemId,
                title,
                "카페",
                BigDecimal.valueOf(37.5445),
                BigDecimal.valueOf(127.0560),
                "https://place.map.kakao.com/" + itemId
        );
    }

    private UserPreference enabledPreference() {
        return UserPreference.builder()
                .user(user)
                .personalizationEnabled(true)
                .preferredTags(Set.of())
                .dislikedTags(Set.of())
                .updatedAt(LocalDateTime.of(2026, 9, 1, 0, 0))
                .build();
    }
}
