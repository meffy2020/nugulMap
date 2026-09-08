package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.LocationLetterReport;

import java.time.LocalDateTime;

public record LocationLetterReportResponse(
        Long id,
        Long letterId,
        String reason,
        String status,
        LocalDateTime createdAt
) {
    public static LocationLetterReportResponse from(LocationLetterReport report) {
        return new LocationLetterReportResponse(
                report.getId(),
                report.getLetter().getId(),
                report.getReason().name(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
