package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.UserBlock;
import com.neogulmap.neogul_map.domain.ZoneReview;
import com.neogulmap.neogul_map.domain.ZoneReviewReport;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import com.neogulmap.neogul_map.domain.enums.ReviewReportReason;
import com.neogulmap.neogul_map.dto.ReviewReportRequest;
import com.neogulmap.neogul_map.dto.ReviewReportOperatorResponse;
import com.neogulmap.neogul_map.dto.ReviewReportResponse;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.repository.UserBlockRepository;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewReportRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class ReviewModerationService {

    private static final int MAX_REPORT_DETAILS_LENGTH = 500;

    private final ZoneReviewRepository zoneReviewRepository;
    private final ZoneReviewReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final Clock clock;

    @Autowired
    public ReviewModerationService(
            ZoneReviewRepository zoneReviewRepository,
            ZoneReviewReportRepository reportRepository,
            UserRepository userRepository,
            UserBlockRepository userBlockRepository
    ) {
        this(
                zoneReviewRepository,
                reportRepository,
                userRepository,
                userBlockRepository,
                Clock.systemUTC()
        );
    }

    ReviewModerationService(
            ZoneReviewRepository zoneReviewRepository,
            ZoneReviewReportRepository reportRepository,
            UserRepository userRepository,
            UserBlockRepository userBlockRepository,
            Clock clock
    ) {
        this.zoneReviewRepository = zoneReviewRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
        this.clock = clock;
    }

    @Transactional
    public ReviewReportResponse reportReview(
            Integer zoneId,
            Long reviewId,
            ReviewReportRequest request,
            User reporter
    ) {
        requireAuthenticated(reporter);
        ZoneReview review = zoneReviewRepository.findByIdWithAuthorAndZone(reviewId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REVIEW_NOT_FOUND));
        if (review.getZone() == null || !zoneId.equals(review.getZone().getId())) {
            throw new NotFoundException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (reportRepository.existsByReviewIdAndReporterId(reviewId, reporter.getId())) {
            throw new BusinessBaseException(
                    ErrorCode.REVIEW_DUPLICATE_REPORT,
                    "이미 신고한 리뷰입니다."
            );
        }

        ReviewReportReason reason = parseReason(request == null ? null : request.reason());
        String details = normalizeDetails(request == null ? null : request.details());
        if (reason == ReviewReportReason.OTHER && details == null) {
            throw new ValidationException(
                    ErrorCode.REQUIRED_FIELD_MISSING,
                    "기타 신고 사유의 상세 내용을 입력해주세요."
            );
        }

        ZoneReviewReport report = reportRepository.save(ZoneReviewReport.builder()
                .review(review)
                .reporter(reporter)
                .reason(reason)
                .details(details)
                .status(ModerationStatus.PENDING)
                .build());
        return ReviewReportResponse.from(report);
    }

    @Transactional
    public void blockUser(Long blockedUserId, User blocker) {
        requireAuthenticated(blocker);
        if (blockedUserId == null || blockedUserId.equals(blocker.getId())) {
            throw new ValidationException(ErrorCode.USER_BLOCK_INVALID, "본인 계정은 차단할 수 없습니다.");
        }
        User blocked = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blockedUserId)) {
            return;
        }
        userBlockRepository.save(UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build());
    }

    @Transactional
    public void unblockUser(Long blockedUserId, User blocker) {
        requireAuthenticated(blocker);
        userBlockRepository.deleteByBlockerIdAndBlockedId(blocker.getId(), blockedUserId);
    }

    @Transactional(readOnly = true)
    public List<ReviewReportOperatorResponse> getPendingReports() {
        return reportRepository.findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus.PENDING)
                .stream()
                .map(ReviewReportOperatorResponse::from)
                .toList();
    }

    @Transactional
    public ModerationDecisionResponse decideReport(Long reportId, ModerationDecisionRequest request) {
        ZoneReviewReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MODERATION_REPORT_NOT_FOUND));
        requirePending(report.getStatus());

        String action = parseDecisionAction(request == null ? null : request.action());
        if ("REMOVE_CONTENT".equals(action)) {
            ZoneReview review = report.getReview();
            Long reviewId = review.getId();
            reportRepository.deleteByReviewId(reviewId);
            zoneReviewRepository.delete(review);
            return new ModerationDecisionResponse(
                    reportId,
                    action,
                    ModerationStatus.RESOLVED.name(),
                    true
            );
        }

        ModerationStatus status = "DISMISS".equals(action)
                ? ModerationStatus.DISMISSED
                : ModerationStatus.RESOLVED;
        report.setStatus(status);
        report.setResolvedAt(now());
        reportRepository.save(report);
        return new ModerationDecisionResponse(reportId, action, status.name(), false);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requireAuthenticated(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessBaseException(ErrorCode.ZONE_ACCESS_DENIED, "로그인이 필요합니다.");
        }
    }

    private ReviewReportReason parseReason(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "신고 사유를 선택해주세요.");
        }
        try {
            return ReviewReportReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 신고 사유입니다.");
        }
    }

    private String normalizeDetails(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REPORT_DETAILS_LENGTH) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "신고 상세 내용은 500자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private void requirePending(ModerationStatus status) {
        if (status != ModerationStatus.PENDING) {
            throw new ValidationException(
                    ErrorCode.MODERATION_DECISION_INVALID,
                    "이미 처리된 신고입니다."
            );
        }
    }

    private String parseDecisionAction(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "처리 결정을 선택해주세요.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("RESOLVE")
                && !normalized.equals("DISMISS")
                && !normalized.equals("REMOVE_CONTENT")) {
            throw new ValidationException(ErrorCode.MODERATION_DECISION_INVALID);
        }
        return normalized;
    }
}
