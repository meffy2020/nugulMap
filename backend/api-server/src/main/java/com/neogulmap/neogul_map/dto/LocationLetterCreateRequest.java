package com.neogulmap.neogul_map.dto;

public record LocationLetterCreateRequest(
        Double latitude,
        Double longitude,
        String content
) {
}
