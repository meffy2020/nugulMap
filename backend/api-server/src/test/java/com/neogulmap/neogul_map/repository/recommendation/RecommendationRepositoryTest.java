package com.neogulmap.neogul_map.repository.recommendation;

import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.recommendation.BudgetLevel;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationInteraction;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import com.neogulmap.neogul_map.domain.recommendation.UserPreference;
import com.neogulmap.neogul_map.domain.recommendation.UserSavedItem;
import com.neogulmap.neogul_map.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class RecommendationRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;
    @Autowired private UserSavedItemRepository userSavedItemRepository;
    @Autowired private RecommendationInteractionRepository recommendationInteractionRepository;

    @Test
    void preferencePersistsOptInSettingsAndJsonTagSets() {
        User user = saveUser("preference");
        UserPreference preference = userPreferenceRepository.saveAndFlush(UserPreference.builder()
                .user(user)
                .personalizationEnabled(true)
                .preferredTags(new LinkedHashSet<>(List.of("조용한", "디저트")))
                .dislikedTags(new LinkedHashSet<>(List.of("웨이팅")))
                .budgetLevel(BudgetLevel.MEDIUM)
                .maxDistanceMeters(3_000)
                .updatedAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .build());

        UserPreference found = userPreferenceRepository.findByUser_Id(user.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(user.getId());
        assertThat(found.isPersonalizationEnabled()).isTrue();
        assertThat(found.getPreferredTags()).containsExactly("조용한", "디저트");
        assertThat(found.getDislikedTags()).containsExactly("웨이팅");
        assertThat(found.getBudgetLevel()).isEqualTo(BudgetLevel.MEDIUM);
        assertThat(preference.getMaxDistanceMeters()).isEqualTo(3_000);
    }

    @Test
    void savedItemProviderKeyIsUniquePerUser() {
        User user = saveUser("saved-item");
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 9, 0);
        userSavedItemRepository.saveAndFlush(savedItem(user, "123", "첫 저장", now));

        assertThat(userSavedItemRepository.countByUser_Id(user.getId())).isEqualTo(1);

        assertThatThrownBy(() -> userSavedItemRepository.saveAndFlush(
                savedItem(user, "123", "중복 저장", now.plusMinutes(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void interactionRepositoryReturnsLatestNotInterestedEventsAndDeletesAllUserData() {
        User user = saveUser("interaction");
        LocalDateTime first = LocalDateTime.of(2026, 9, 1, 9, 0);
        recommendationInteractionRepository.save(interaction(
                user, "item-old", RecommendationEventType.NOT_INTERESTED, first));
        recommendationInteractionRepository.save(interaction(
                user, "item-impression", RecommendationEventType.IMPRESSION, first.plusMinutes(1)));
        recommendationInteractionRepository.saveAndFlush(interaction(
                user, "item-new", RecommendationEventType.NOT_INTERESTED, first.plusMinutes(2)));

        List<RecommendationInteraction> latest = recommendationInteractionRepository
                .findByUser_IdAndEventTypeOrderByOccurredAtDesc(
                        user.getId(), RecommendationEventType.NOT_INTERESTED, PageRequest.of(0, 1));

        assertThat(latest).extracting(RecommendationInteraction::getItemId).containsExactly("item-new");
        assertThat(recommendationInteractionRepository.countByUser_Id(user.getId())).isEqualTo(3);
        assertThat(recommendationInteractionRepository
                .findByUser_IdAndRequestIdAndItemTypeAndProviderAndItemIdAndEventType(
                        user.getId(),
                        "request-1",
                        RecommendationItemType.PLACE,
                        "kakao",
                        "item-new",
                        RecommendationEventType.NOT_INTERESTED
                ))
                .isPresent();

        recommendationInteractionRepository.deleteByUser_Id(user.getId());
        recommendationInteractionRepository.flush();
        assertThat(recommendationInteractionRepository.findAll()).isEmpty();
    }

    @Test
    void interactionIdempotencyKeyIsUniquePerUser() {
        User user = saveUser("interaction-idempotency");
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 9, 0);
        recommendationInteractionRepository.saveAndFlush(interaction(
                user, "same-item", RecommendationEventType.DETAIL_OPEN, now));

        assertThatThrownBy(() -> recommendationInteractionRepository.saveAndFlush(interaction(
                user, "same-item", RecommendationEventType.DETAIL_OPEN, now.plusMinutes(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User saveUser(String suffix) {
        return userRepository.saveAndFlush(User.builder()
                .email(suffix + "@example.com")
                .oauthId("oauth-" + suffix)
                .oauthProvider("test")
                .nickname("테스트 사용자")
                .build());
    }

    private UserSavedItem savedItem(User user, String itemId, String title, LocalDateTime timestamp) {
        return UserSavedItem.builder()
                .user(user)
                .itemType(RecommendationItemType.PLACE)
                .provider("kakao")
                .itemId(itemId)
                .title(title)
                .category("카페")
                .lat(BigDecimal.valueOf(37.5445))
                .lng(BigDecimal.valueOf(127.0560))
                .sourceUrl("https://place.map.kakao.com/" + itemId)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();
    }

    private RecommendationInteraction interaction(
            User user,
            String itemId,
            RecommendationEventType eventType,
            LocalDateTime occurredAt
    ) {
        return RecommendationInteraction.builder()
                .user(user)
                .requestId("request-1")
                .itemType(RecommendationItemType.PLACE)
                .provider("kakao")
                .itemId(itemId)
                .eventType(eventType)
                .intent("CAFE")
                .contextRegion("성수")
                .modelVersion("context-v1")
                .occurredAt(occurredAt)
                .build();
    }
}
