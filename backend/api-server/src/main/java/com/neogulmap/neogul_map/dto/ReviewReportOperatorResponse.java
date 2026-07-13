package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.ZoneReview;
import com.neogulmap.neogul_map.domain.ZoneReviewReport;

import java.time.LocalDateTime;

public record ReviewReportOperatorResponse(
        Long id,
        Long reviewId,
        Integer zoneId,
        Long authorId,
        Long reporterId,
        String content,
        String reason,
        String details,
        String status,
        LocalDateTime createdAt
) {
    public static ReviewReportOperatorResponse from(ZoneReviewReport report) {
        ZoneReview review = report.getReview();
        Long authorId = review.getAuthor() == null ? null : review.getAuthor().getId();
        return new ReviewReportOperatorResponse(
                report.getId(),
                review.getId(),
                review.getZone().getId(),
                authorId,
                report.getReporter().getId(),
                review.getContent(),
                report.getReason().name(),
                report.getDetails(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
