package com.neogulmap.neogul_map.dto;

import java.time.Instant;
import java.util.List;

public record EventInsightResponse(
        List<EventInsightItem> events,
        String dataFreshness,
        Instant updatedAt,
        List<String> sources
) {
    public record EventInsightItem(
            String id,
            String title,
            String kind,
            String period,
            String startDate,
            String endDate,
            Double latitude,
            Double longitude,
            String address,
            String imageUrl,
            String source,
            String sourceContentId
    ) {
    }
}
