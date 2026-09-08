package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;

import java.math.BigDecimal;

public record UserSavedItemRequest(
        RecommendationItemType itemType,
        String provider,
        String itemId,
        String title,
        String category,
        BigDecimal lat,
        BigDecimal lng,
        String sourceUrl
) {
}
