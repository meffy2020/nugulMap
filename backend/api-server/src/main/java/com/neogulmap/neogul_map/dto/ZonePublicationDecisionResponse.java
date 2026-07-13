package com.neogulmap.neogul_map.dto;

public record ZonePublicationDecisionResponse(
        Integer zoneId,
        String action,
        String status,
        boolean contentRemoved
) {
}
