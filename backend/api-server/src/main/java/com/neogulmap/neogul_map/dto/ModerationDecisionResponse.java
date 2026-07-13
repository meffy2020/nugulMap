package com.neogulmap.neogul_map.dto;

public record ModerationDecisionResponse(
        Long reportId,
        String action,
        String status,
        boolean contentRemoved
) {
}
