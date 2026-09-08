package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationInteraction;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;

import java.time.LocalDateTime;

public record RecommendationInteractionResponse(
        Long id,
        String requestId,
        RecommendationItemType itemType,
        String provider,
        String itemId,
        RecommendationEventType eventType,
        String intent,
        String contextRegion,
        String modelVersion,
        LocalDateTime occurredAt
) {
    public static RecommendationInteractionResponse from(RecommendationInteraction interaction) {
        return new RecommendationInteractionResponse(
                interaction.getId(),
                interaction.getRequestId(),
                interaction.getItemType(),
                interaction.getProvider(),
                interaction.getItemId(),
                interaction.getEventType(),
                interaction.getIntent(),
                interaction.getContextRegion(),
                interaction.getModelVersion(),
                interaction.getOccurredAt()
        );
    }
}
