package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReview;
import com.neogulmap.neogul_map.domain.enums.ZonePublicationStatus;
import com.neogulmap.neogul_map.dto.ZoneReviewRequest;
import com.neogulmap.neogul_map.dto.ZoneReviewResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewRepository;
import com.neogulmap.neogul_map.repository.UserBlockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.Set;

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

    @Mock
    private UserBlockRepository userBlockRepository;

    private final ReviewContentPolicy reviewContentPolicy = new ReviewContentPolicy(
            "씨발,시발,개새끼,병신,좆,fuck"
    );

    private ZoneReviewService zoneReviewService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        zoneReviewService = new ZoneReviewService(
                zoneRepository,
                zoneReviewRepository,
                userBlockRepository,
                reviewContentPolicy
        );
    }

    @Test
    @DisplayName("리뷰는 공백을 정리해 저장하고 작성자 정보를 응답한다")
    void createReviewTrimsContent() {
        Zone zone = validZone();
        User user = User.builder().id(5L).email("reviewer@nugulmap.com").nickname("리뷰어").build();
        when(zoneRepository.findByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(zone));
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
    @DisplayName("우회 공백이 포함된 공격적 표현은 리뷰로 저장하지 않는다")
    void offensiveReviewIsRejected() {
        assertThatThrownBy(() -> zoneReviewService.createReview(
                10,
                ZoneReviewRequest.builder().content("씨  발 여기는 최악").build(),
                User.builder().id(1L).email("user@nugulmap.com").build()
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("부적절한 표현");

        verify(zoneReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인 사용자가 차단한 작성자의 리뷰는 조회 결과에서 제외한다")
    void getReviewsFiltersBlockedAuthorsForViewer() {
        User viewer = User.builder().id(1L).email("viewer@nugulmap.com").build();
        User visibleAuthor = User.builder().id(2L).nickname("보이는사람").build();
        User blockedAuthor = User.builder().id(3L).nickname("차단된사람").build();
        when(zoneRepository.existsByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(true);
        when(zoneReviewRepository.findByZoneIdWithAuthorOrderByCreatedAtDesc(10)).thenReturn(List.of(
                ZoneReview.builder().id(1L).zone(validZone()).author(blockedAuthor).content("숨김").build(),
                ZoneReview.builder().id(2L).zone(validZone()).author(visibleAuthor).content("표시").build()
        ));
        when(userBlockRepository.findBlockedUserIdsByBlockerId(1L)).thenReturn(Set.of(3L));

        List<ZoneReviewResponse> reviews = zoneReviewService.getReviews(10, viewer);

        assertThat(reviews).extracting(ZoneReviewResponse::getAuthorId).containsExactly(2L);
    }

    @Test
    @DisplayName("작성자 계정이 삭제된 레거시 리뷰도 익명으로 조회해 신고할 수 있게 한다")
    void getReviewsIncludesLegacyReviewWithoutAuthor() {
        when(zoneRepository.existsByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(true);
        when(zoneReviewRepository.findByZoneIdWithAuthorOrderByCreatedAtDesc(10)).thenReturn(List.of(
                ZoneReview.builder()
                        .id(4L)
                        .zone(validZone())
                        .author(null)
                        .content("레거시 리뷰")
                        .build()
        ));

        List<ZoneReviewResponse> reviews = zoneReviewService.getReviews(10, null);

        assertThat(reviews).singleElement().satisfies(review -> {
            assertThat(review.getId()).isEqualTo(4L);
            assertThat(review.getAuthorId()).isNull();
            assertThat(review.getAuthorNickname()).isEqualTo("익명사용자");
        });
    }

    @Test
    @DisplayName("존재하지 않는 Zone에는 리뷰를 작성할 수 없다")
    void reviewRequiresExistingZone() {
        when(zoneRepository.findByIdAndPublicationStatus(404, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.empty());

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
