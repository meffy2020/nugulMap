package com.neogulmap.neogul_map.dto;

public record LocationLetterLikeResponse(
        Long letterId,
        long likeCount,
        boolean likedByMe
) {
}
