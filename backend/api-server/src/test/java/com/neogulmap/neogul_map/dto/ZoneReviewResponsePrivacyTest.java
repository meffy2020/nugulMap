package com.neogulmap.neogul_map.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReview;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneReviewResponsePrivacyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicReviewJsonDoesNotContainAuthorEmail() throws Exception {
        ZoneReview review = ZoneReview.builder()
                .id(7L)
                .zone(Zone.builder().id(10).build())
                .author(User.builder()
                        .id(3L)
                        .nickname("리뷰어")
                        .email("private@nugulmap.com")
                        .build())
                .content("깨끗해요")
                .build();

        String json = objectMapper.writeValueAsString(ZoneReviewResponse.from(review));

        assertThat(json).contains("\"authorNickname\":\"리뷰어\"");
        assertThat(json).doesNotContain("authorEmail", "private@nugulmap.com");
    }
}
