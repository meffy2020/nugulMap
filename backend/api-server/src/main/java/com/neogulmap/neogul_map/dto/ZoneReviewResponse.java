package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.ZoneReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneReviewResponse {
    private Long id;
    private Integer zoneId;
    private Long authorId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;

    public static ZoneReviewResponse from(ZoneReview review) {
        if (review == null) {
            return null;
        }

        String authorNickname = PublicUserLabel.from(review.getAuthor());

        return ZoneReviewResponse.builder()
                .id(review.getId())
                .zoneId(review.getZone() != null ? review.getZone().getId() : null)
                .authorId(review.getAuthor() != null ? review.getAuthor().getId() : null)
                .authorNickname(authorNickname)
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
