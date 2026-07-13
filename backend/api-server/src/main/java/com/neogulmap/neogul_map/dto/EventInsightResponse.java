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
            String sourceContentId,
            String detailUrl,
            String collectedAt,
            String attribution,
            String sourceUrl,
            String license,
            String licenseUrl
    ) {
        public EventInsightItem(
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
                String sourceContentId,
                String detailUrl,
                String collectedAt,
                String attribution
        ) {
            this(
                    id,
                    title,
                    kind,
                    period,
                    startDate,
                    endDate,
                    latitude,
                    longitude,
                    address,
                    imageUrl,
                    source,
                    sourceContentId,
                    detailUrl,
                    collectedAt,
                    attribution,
                    null,
                    null,
                    null
            );
        }

        public EventInsightItem(
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
                String sourceContentId,
                String detailUrl,
                String collectedAt
        ) {
            this(
                    id,
                    title,
                    kind,
                    period,
                    startDate,
                    endDate,
                    latitude,
                    longitude,
                    address,
                    imageUrl,
                    source,
                    sourceContentId,
                    detailUrl,
                    collectedAt,
                    null,
                    null,
                    null,
                    null
            );
        }

        public EventInsightItem(
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
            this(
                    id,
                    title,
                    kind,
                    period,
                    startDate,
                    endDate,
                    latitude,
                    longitude,
                    address,
                    imageUrl,
                    source,
                    sourceContentId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
