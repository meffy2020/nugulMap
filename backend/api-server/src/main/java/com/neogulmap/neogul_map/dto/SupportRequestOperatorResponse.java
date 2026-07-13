package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.SupportRequest;

import java.time.LocalDateTime;

public record SupportRequestOperatorResponse(
        Long id,
        String category,
        String email,
        String message,
        String status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public static SupportRequestOperatorResponse from(SupportRequest request) {
        return new SupportRequestOperatorResponse(
                request.getId(),
                request.getCategory().name(),
                request.getEmail(),
                request.getMessage(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getResolvedAt()
        );
    }
}
