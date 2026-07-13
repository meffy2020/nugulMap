package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.ZoneReviewReport;

import java.time.LocalDateTime;

public record ReviewReportResponse(
        Long id,
        Long reviewId,
        String reason,
        String status,
        LocalDateTime createdAt
) {
    public static ReviewReportResponse from(ZoneReviewReport report) {
        return new ReviewReportResponse(
                report.getId(),
                report.getReview().getId(),
                report.getReason().name(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
