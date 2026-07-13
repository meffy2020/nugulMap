package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.UserBlock;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReview;
import com.neogulmap.neogul_map.domain.ZoneReviewReport;
import com.neogulmap.neogul_map.dto.ReviewReportRequest;
import com.neogulmap.neogul_map.dto.ReviewReportOperatorResponse;
import com.neogulmap.neogul_map.dto.ReviewReportResponse;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.repository.UserBlockRepository;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewReportRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewModerationServiceTest {

    @Mock private ZoneReviewRepository zoneReviewRepository;
    @Mock private ZoneReviewReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserBlockRepository userBlockRepository;

    private ReviewModerationService service;

    @BeforeEach
    void setUp() {
        service = new ReviewModerationService(
                zoneReviewRepository,
                reportRepository,
                userRepository,
                userBlockRepository,
                Clock.fixed(Instant.parse("2026-07-12T08:15:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void authenticatedUserCanReportReviewIntoPendingModerationQueue() {
        User reporter = User.builder().id(1L).email("reporter@nugulmap.com").build();
        ZoneReview review = ZoneReview.builder()
                .id(9L)
                .zone(Zone.builder().id(10).build())
                .author(User.builder().id(2L).build())
                .content("문제 리뷰")
                .build();
        when(zoneReviewRepository.findByIdWithAuthorAndZone(9L)).thenReturn(Optional.of(review));
        when(reportRepository.existsByReviewIdAndReporterId(9L, 1L)).thenReturn(false);
        when(reportRepository.save(any(ZoneReviewReport.class))).thenAnswer(invocation -> {
            ZoneReviewReport report = invocation.getArgument(0);
            report.setId(15L);
            report.onCreate();
            return report;
        });

        ReviewReportResponse response = service.reportReview(
                10,
                9L,
                new ReviewReportRequest("HARASSMENT", "반복적인 모욕"),
                reporter
        );

        assertThat(response.id()).isEqualTo(15L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.reason()).isEqualTo("HARASSMENT");
    }

    @Test
    void legacyReviewWithoutAuthorRemainsReportableByReviewId() {
        User reporter = User.builder().id(1L).email("reporter@nugulmap.com").build();
        ZoneReview legacyReview = ZoneReview.builder()
                .id(10L)
                .zone(Zone.builder().id(20).build())
                .author(null)
                .content("작성자 계정이 삭제된 리뷰")
                .build();
        when(zoneReviewRepository.findByIdWithAuthorAndZone(10L)).thenReturn(Optional.of(legacyReview));
        when(reportRepository.existsByReviewIdAndReporterId(10L, 1L)).thenReturn(false);
        when(reportRepository.save(any(ZoneReviewReport.class))).thenAnswer(invocation -> {
            ZoneReviewReport report = invocation.getArgument(0);
            report.setId(16L);
            report.onCreate();
            return report;
        });

        ReviewReportResponse response = service.reportReview(
                20,
                10L,
                new ReviewReportRequest("SPAM", null),
                reporter
        );

        assertThat(response.id()).isEqualTo(16L);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void duplicateReportIsRejectedWithoutWritingAgain() {
        User reporter = User.builder().id(1L).build();
        ZoneReview review = ZoneReview.builder()
                .id(9L)
                .zone(Zone.builder().id(10).build())
                .author(User.builder().id(2L).build())
                .build();
        when(zoneReviewRepository.findByIdWithAuthorAndZone(9L)).thenReturn(Optional.of(review));
        when(reportRepository.existsByReviewIdAndReporterId(9L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.reportReview(
                10, 9L, new ReviewReportRequest("SPAM", null), reporter
        )).isInstanceOf(BusinessBaseException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void authenticatedUserCanBlockAnotherUserIdempotently() {
        User blocker = User.builder().id(1L).build();
        User blocked = User.builder().id(2L).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);

        service.blockUser(2L, blocker);

        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    void userCannotBlockSelf() {
        User user = User.builder().id(1L).build();

        assertThatThrownBy(() -> service.blockUser(1L, user))
                .isInstanceOf(BusinessBaseException.class);

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void legacyReviewWithoutAuthorCannotTriggerUserBlock() {
        User blocker = User.builder().id(1L).build();

        assertThatThrownBy(() -> service.blockUser(null, blocker))
                .isInstanceOf(BusinessBaseException.class);

        verify(userRepository, never()).findById(any());
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void operatorQueueReturnsOldestPendingReportsWithoutUserEmail() {
        ZoneReviewReport report = ZoneReviewReport.builder()
                .id(15L)
                .review(ZoneReview.builder()
                        .id(9L)
                        .zone(Zone.builder().id(10).build())
                        .author(User.builder().id(2L).email("author@example.com").build())
                        .content("문제 리뷰")
                        .build())
                .reporter(User.builder().id(1L).email("reporter@example.com").build())
                .reason(com.neogulmap.neogul_map.domain.enums.ReviewReportReason.HARASSMENT)
                .details("반복적인 모욕")
                .status(com.neogulmap.neogul_map.domain.enums.ModerationStatus.PENDING)
                .createdAt(LocalDateTime.parse("2026-07-10T10:00:00"))
                .build();
        when(reportRepository.findTop100ByStatusOrderByCreatedAtAsc(
                com.neogulmap.neogul_map.domain.enums.ModerationStatus.PENDING
        )).thenReturn(List.of(report));

        List<ReviewReportOperatorResponse> queue = service.getPendingReports();

        assertThat(queue).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(15L);
            assertThat(item.zoneId()).isEqualTo(10);
            assertThat(item.reviewId()).isEqualTo(9L);
            assertThat(item.authorId()).isEqualTo(2L);
            assertThat(item.reporterId()).isEqualTo(1L);
            assertThat(item.content()).isEqualTo("문제 리뷰");
        });
    }

    @Test
    void operatorQueueSupportsReportedLegacyReviewWithoutAuthor() {
        ZoneReviewReport report = ZoneReviewReport.builder()
                .id(16L)
                .review(ZoneReview.builder()
                        .id(10L)
                        .zone(Zone.builder().id(20).build())
                        .author(null)
                        .content("작성자 계정이 삭제된 리뷰")
                        .build())
                .reporter(User.builder().id(1L).build())
                .reason(com.neogulmap.neogul_map.domain.enums.ReviewReportReason.SPAM)
                .status(com.neogulmap.neogul_map.domain.enums.ModerationStatus.PENDING)
                .createdAt(LocalDateTime.parse("2026-07-11T10:00:00"))
                .build();
        when(reportRepository.findTop100ByStatusOrderByCreatedAtAsc(
                com.neogulmap.neogul_map.domain.enums.ModerationStatus.PENDING
        )).thenReturn(List.of(report));

        List<ReviewReportOperatorResponse> queue = service.getPendingReports();

        assertThat(queue).singleElement().satisfies(item -> {
            assertThat(item.reviewId()).isEqualTo(10L);
            assertThat(item.authorId()).isNull();
            assertThat(item.content()).isEqualTo("작성자 계정이 삭제된 리뷰");
        });
    }

    @Test
    void operatorCanDismissPendingReviewReport() {
        ZoneReviewReport report = pendingReport();
        when(reportRepository.findById(15L)).thenReturn(Optional.of(report));

        ModerationDecisionResponse response = service.decideReport(
                15L,
                new ModerationDecisionRequest("DISMISS")
        );

        assertThat(response.status()).isEqualTo("DISMISSED");
        assertThat(response.contentRemoved()).isFalse();
        assertThat(report.getStatus()).isEqualTo(
                com.neogulmap.neogul_map.domain.enums.ModerationStatus.DISMISSED
        );
        assertThat(report.getResolvedAt()).isEqualTo(LocalDateTime.parse("2026-07-12T08:15:00"));
        verify(reportRepository).save(report);
        verify(zoneReviewRepository, never()).delete(any());
    }

    @Test
    void operatorCanRemoveReportedReviewContent() {
        ZoneReviewReport report = pendingReport();
        when(reportRepository.findById(15L)).thenReturn(Optional.of(report));

        ModerationDecisionResponse response = service.decideReport(
                15L,
                new ModerationDecisionRequest("REMOVE_CONTENT")
        );

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.contentRemoved()).isTrue();
        verify(reportRepository).deleteByReviewId(9L);
        verify(zoneReviewRepository).delete(report.getReview());
    }

    private ZoneReviewReport pendingReport() {
        return ZoneReviewReport.builder()
                .id(15L)
                .review(ZoneReview.builder()
                        .id(9L)
                        .zone(Zone.builder().id(10).build())
                        .author(User.builder().id(2L).build())
                        .content("문제 리뷰")
                        .build())
                .reporter(User.builder().id(1L).build())
                .reason(com.neogulmap.neogul_map.domain.enums.ReviewReportReason.HARASSMENT)
                .status(com.neogulmap.neogul_map.domain.enums.ModerationStatus.PENDING)
                .build();
    }
}
