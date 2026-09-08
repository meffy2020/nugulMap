package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import com.neogulmap.neogul_map.domain.recommendation.UserSavedItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserSavedItemResponse(
        Long id,
        RecommendationItemType itemType,
        String provider,
        String itemId,
        String title,
        String category,
        BigDecimal lat,
        BigDecimal lng,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserSavedItemResponse from(UserSavedItem item) {
        return new UserSavedItemResponse(
                item.getId(),
                item.getItemType(),
                item.getProvider(),
                item.getItemId(),
                item.getTitle(),
                item.getCategory(),
                item.getLat(),
                item.getLng(),
                item.getSourceUrl(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
