package com.neogulmap.neogul_map.dto;

public record SupportRequestCreateRequest(
        String category,
        String email,
        String message,
        String website
) {
}
