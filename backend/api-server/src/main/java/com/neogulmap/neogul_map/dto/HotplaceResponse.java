package com.neogulmap.neogul_map.dto;

import java.time.Instant;
import java.util.List;

public record HotplaceResponse(
        List<HotplaceItem> places,
        String dataFreshness,
        Instant updatedAt,
        List<String> sources
) {
    public record HotplaceItem(
            String id,
            String name,
            String category,
            String crowdLevel,
            String crowdMessage,
            Integer estimatedMinPeople,
            Integer estimatedMaxPeople,
            Double latitude,
            Double longitude,
            String address,
            String source,
            String sourcePlaceCode,
            String updatedAt,
            String freshnessStatus,
            Long ageSeconds
    ) {
        public HotplaceItem(
                String id,
                String name,
                String category,
                String crowdLevel,
                String crowdMessage,
                Integer estimatedMinPeople,
                Integer estimatedMaxPeople,
                Double latitude,
                Double longitude,
                String address,
                String source,
                String sourcePlaceCode,
                String updatedAt
        ) {
            this(
                    id,
                    name,
                    category,
                    crowdLevel,
                    crowdMessage,
                    estimatedMinPeople,
                    estimatedMaxPeople,
                    latitude,
                    longitude,
                    address,
                    source,
                    sourcePlaceCode,
                    updatedAt,
                    null,
                    null
            );
        }
    }
}
