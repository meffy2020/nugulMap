package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.SupportRequest;

import java.time.LocalDateTime;

public record SupportRequestResponse(
        Long id,
        String category,
        String status,
        LocalDateTime createdAt
) {
    public static SupportRequestResponse from(SupportRequest request) {
        return new SupportRequestResponse(
                request.getId(),
                request.getCategory().name(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
