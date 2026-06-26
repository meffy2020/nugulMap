package com.neogulmap.neogul_map.dto;

import java.time.Instant;

public record MapInsightResponse(
        HotplaceResponse hotplaces,
        EventInsightResponse events,
        InsightStatusResponse status,
        Instant updatedAt
) {
}
