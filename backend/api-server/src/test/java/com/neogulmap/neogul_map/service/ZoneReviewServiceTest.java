package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReview;
import com.neogulmap.neogul_map.dto.ZoneReviewRequest;
import com.neogulmap.neogul_map.dto.ZoneReviewResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneReviewServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ZoneReviewRepository zoneReviewRepository;

    @InjectMocks
    private ZoneReviewService zoneReviewService;

    @Test
    @DisplayName("리뷰는 공백을 정리해 저장하고 작성자 정보를 응답한다")
    void createReviewTrimsContent() {
        Zone zone = validZone();
        User user = User.builder().id(5L).email("reviewer@nugulmap.com").nickname("리뷰어").build();
        when(zoneRepository.findById(10)).thenReturn(Optional.of(zone));
        when(zoneReviewRepository.save(any(ZoneReview.class))).thenAnswer(invocation -> {
            ZoneReview review = invocation.getArgument(0);
            review.setId(3L);
            review.onCreate();
            return review;
        });

        ZoneReviewResponse response = zoneReviewService.createReview(
                10,
                ZoneReviewRequest.builder().content("  깨끗해요  ").build(),
                user
        );

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getContent()).isEqualTo("깨끗해요");
        assertThat(response.getAuthorNickname()).isEqualTo("리뷰어");
    }

    @Test
    @DisplayName("비로그인 사용자는 리뷰를 작성할 수 없다")
    void anonymousUserCannotCreateReview() {
        assertThatThrownBy(() -> zoneReviewService.createReview(
                10,
                ZoneReviewRequest.builder().content("내용").build(),
                null
        )).isInstanceOf(BusinessBaseException.class);

        verify(zoneReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("빈 리뷰는 저장하지 않는다")
    void blankReviewIsRejected() {
        assertThatThrownBy(() -> zoneReviewService.createReview(
                10,
                ZoneReviewRequest.builder().content("   ").build(),
                User.builder().id(1L).email("user@nugulmap.com").build()
        )).isInstanceOf(ValidationException.class);

        verify(zoneReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 Zone에는 리뷰를 작성할 수 없다")
    void reviewRequiresExistingZone() {
        when(zoneRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneReviewService.createReview(
                404,
                ZoneReviewRequest.builder().content("내용").build(),
                User.builder().id(1L).email("user@nugulmap.com").build()
        )).isInstanceOf(NotFoundException.class);
    }

    private Zone validZone() {
        return Zone.builder()
                .id(10)
                .region("서울")
                .type("흡연구역")
                .latitude(BigDecimal.valueOf(37.5665))
                .longitude(BigDecimal.valueOf(126.9780))
                .address("서울특별시 중구 세종대로")
                .build();
    }
}
