package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReport;

import java.time.LocalDateTime;

public record ZoneReportOperatorResponse(
        Long id,
        Integer zoneId,
        Long creatorId,
        Long reporterId,
        String region,
        String address,
        String description,
        String reason,
        String details,
        String status,
        LocalDateTime createdAt
) {
    public static ZoneReportOperatorResponse from(ZoneReport report) {
        Zone zone = report.getZone();
        Long creatorId = zone.getCreator() == null ? null : zone.getCreator().getId();
        return new ZoneReportOperatorResponse(
                report.getId(),
                zone.getId(),
                creatorId,
                report.getReporter().getId(),
                zone.getRegion(),
                zone.getAddress(),
                zone.getDescription(),
                report.getReason().name(),
                report.getDetails(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
