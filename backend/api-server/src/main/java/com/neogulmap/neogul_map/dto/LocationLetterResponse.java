package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.LocationLetter;
import com.neogulmap.neogul_map.domain.User;

import java.time.LocalDateTime;

public record LocationLetterResponse(
        Long id,
        Long authorId,
        String authorLabel,
        String content,
        boolean mine,
        LocalDateTime createdAt,
        long likeCount,
        boolean likedByMe
) {
    public static LocationLetterResponse from(
            LocationLetter letter,
            User currentUser,
            long likeCount,
            boolean likedByMe
    ) {
        User author = letter.getAuthor();
        boolean mine = currentUser != null
                && currentUser.getId() != null
                && author != null
                && currentUser.getId().equals(author.getId());
        return new LocationLetterResponse(
                letter.getId(),
                currentUser == null || author == null ? null : author.getId(),
                PublicUserLabel.from(author),
                letter.getContent(),
                mine,
                letter.getCreatedAt(),
                Math.max(likeCount, 0),
                likedByMe
        );
    }
}
