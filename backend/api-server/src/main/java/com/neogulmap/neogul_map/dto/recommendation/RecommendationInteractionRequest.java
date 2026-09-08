package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;

import java.time.LocalDateTime;

public record RecommendationInteractionRequest(
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
}
