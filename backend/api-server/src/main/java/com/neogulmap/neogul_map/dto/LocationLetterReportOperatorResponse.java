package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.LocationLetter;
import com.neogulmap.neogul_map.domain.LocationLetterReport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationLetterReportOperatorResponse(
        Long id,
        Long letterId,
        Long authorId,
        Long reporterId,
        BigDecimal latitude,
        BigDecimal longitude,
        String content,
        String reason,
        String details,
        String status,
        LocalDateTime createdAt
) {
    public static LocationLetterReportOperatorResponse from(LocationLetterReport report) {
        LocationLetter letter = report.getLetter();
        return new LocationLetterReportOperatorResponse(
                report.getId(),
                letter.getId(),
                letter.getAuthor() == null ? null : letter.getAuthor().getId(),
                report.getReporter() == null ? null : report.getReporter().getId(),
                letter.getLatitudeBucket(),
                letter.getLongitudeBucket(),
                letter.getContent(),
                report.getReason().name(),
                report.getDetails(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
