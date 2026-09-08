package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;

public record RecommendationItemKeyRequest(
        RecommendationItemType itemType,
        String provider,
        String itemId
) {
}
