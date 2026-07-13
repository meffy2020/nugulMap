package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.ZoneReport;

import java.time.LocalDateTime;

public record ZoneReportResponse(
        Long id,
        Integer zoneId,
        String reason,
        String status,
        LocalDateTime createdAt
) {
    public static ZoneReportResponse from(ZoneReport report) {
        return new ZoneReportResponse(
                report.getId(),
                report.getZone().getId(),
                report.getReason().name(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
