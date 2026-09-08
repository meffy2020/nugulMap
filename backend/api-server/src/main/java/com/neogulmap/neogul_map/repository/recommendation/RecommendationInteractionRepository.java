package com.neogulmap.neogul_map.repository.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationInteraction;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RecommendationInteractionRepository extends JpaRepository<RecommendationInteraction, Long> {

    List<RecommendationInteraction> findByUser_IdAndEventTypeOrderByOccurredAtDesc(
            Long userId,
            RecommendationEventType eventType,
            Pageable pageable
    );

    List<RecommendationInteraction> findByUser_IdAndEventTypeInOrderByOccurredAtDesc(
            Long userId,
            Set<RecommendationEventType> eventTypes,
            Pageable pageable
    );

    Optional<RecommendationInteraction> findByUser_IdAndRequestIdAndItemTypeAndProviderAndItemIdAndEventType(
            Long userId,
            String requestId,
            RecommendationItemType itemType,
            String provider,
            String itemId,
            RecommendationEventType eventType
    );

    long countByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);

    @Modifying
    @Query("delete from RecommendationInteraction interaction where interaction.occurredAt < :cutoff")
    long deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
