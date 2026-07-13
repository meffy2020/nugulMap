package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReport;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import com.neogulmap.neogul_map.domain.enums.ZonePublicationStatus;
import com.neogulmap.neogul_map.domain.enums.ZoneReportReason;
import com.neogulmap.neogul_map.dto.ZoneReportOperatorResponse;
import com.neogulmap.neogul_map.dto.ZoneReportRequest;
import com.neogulmap.neogul_map.dto.ZoneReportResponse;
import com.neogulmap.neogul_map.dto.ZoneSubmissionOperatorResponse;
import com.neogulmap.neogul_map.dto.ZonePublicationDecisionResponse;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.repository.ZoneReportRepository;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class ZoneModerationService {

    private static final int MAX_REPORT_DETAILS_LENGTH = 500;

    private final ZoneRepository zoneRepository;
    private final ZoneReportRepository reportRepository;
    private final ImageService imageService;
    private final Clock clock;

    @Autowired
    public ZoneModerationService(
            ZoneRepository zoneRepository,
            ZoneReportRepository reportRepository,
            ImageService imageService
    ) {
        this(zoneRepository, reportRepository, imageService, Clock.systemUTC());
    }

    ZoneModerationService(
            ZoneRepository zoneRepository,
            ZoneReportRepository reportRepository,
            ImageService imageService,
            Clock clock
    ) {
        this.zoneRepository = zoneRepository;
        this.reportRepository = reportRepository;
        this.imageService = imageService;
        this.clock = clock;
    }

    @Transactional
    public ZoneReportResponse reportZone(Integer zoneId, ZoneReportRequest request, User reporter) {
        requireAuthenticated(reporter);
        Zone zone = zoneRepository.findByIdAndPublicationStatus(zoneId, ZonePublicationStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
        if (reportRepository.existsByZoneIdAndReporterId(zoneId, reporter.getId())) {
            throw new BusinessBaseException(ErrorCode.ZONE_DUPLICATE_REPORT);
        }

        ZoneReportReason reason = parseReason(request == null ? null : request.reason());
        String details = normalizeDetails(request == null ? null : request.details());
        if (reason == ZoneReportReason.OTHER && details == null) {
            throw new ValidationException(
                    ErrorCode.REQUIRED_FIELD_MISSING,
                    "기타 신고 사유의 상세 내용을 입력해주세요."
            );
        }

        ZoneReport report = reportRepository.save(ZoneReport.builder()
                .zone(zone)
                .reporter(reporter)
                .reason(reason)
                .details(details)
                .status(ModerationStatus.PENDING)
                .build());
        return ZoneReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<ZoneReportOperatorResponse> getPendingReports() {
        return reportRepository.findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus.PENDING)
                .stream()
                .map(ZoneReportOperatorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneSubmissionOperatorResponse> getPendingSubmissions() {
        return zoneRepository.findTop100ByPublicationStatusOrderByDateAscIdAsc(ZonePublicationStatus.PENDING)
                .stream()
                .map(ZoneSubmissionOperatorResponse::from)
                .toList();
    }

    @Transactional
    public ZonePublicationDecisionResponse decideSubmission(
            Integer zoneId,
            ModerationDecisionRequest request
    ) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
        if (zone.getPublicationStatus() != ZonePublicationStatus.PENDING) {
            throw new ValidationException(
                    ErrorCode.MODERATION_DECISION_INVALID,
                    "이미 처리된 장소 등록입니다."
            );
        }

        String action = parsePublicationAction(request == null ? null : request.action());
        if ("PUBLISH".equals(action)) {
            zone.setPublicationStatus(ZonePublicationStatus.PUBLISHED);
            zoneRepository.save(zone);
            return new ZonePublicationDecisionResponse(
                    zoneId,
                    action,
                    ZonePublicationStatus.PUBLISHED.name(),
                    false
            );
        }

        String imageName = zone.getImage();
        zoneRepository.delete(zone);
        deleteImageAfterCommit(imageName);
        return new ZonePublicationDecisionResponse(
                zoneId,
                action,
                "REJECTED",
                true
        );
    }

    @Transactional
    public ModerationDecisionResponse decideReport(Long reportId, ModerationDecisionRequest request) {
        ZoneReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MODERATION_REPORT_NOT_FOUND));
        requirePending(report.getStatus());

        String action = parseDecisionAction(request == null ? null : request.action());
        if ("REMOVE_CONTENT".equals(action)) {
            Zone zone = report.getZone();
            String imageName = zone.getImage();
            reportRepository.deleteByZoneId(zone.getId());
            zoneRepository.delete(zone);
            deleteImageAfterCommit(imageName);
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

    private void deleteImageAfterCommit(String imageName) {
        if (imageName == null || imageName.isBlank()) {
            return;
        }
        Runnable deleteImage = () -> {
            try {
                imageService.deleteImage(imageName, ImageType.ZONE);
            } catch (RuntimeException exception) {
                log.error("신고 처리 후 장소 이미지 삭제 실패: {}", imageName, exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteImage.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteImage.run();
            }
        });
    }

    private void requireAuthenticated(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessBaseException(ErrorCode.ZONE_ACCESS_DENIED, "로그인이 필요합니다.");
        }
    }

    private ZoneReportReason parseReason(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "신고 사유를 선택해주세요.");
        }
        try {
            return ZoneReportReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
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

    private String parsePublicationAction(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "처리 결정을 선택해주세요.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("PUBLISH") && !normalized.equals("REJECT")) {
            throw new ValidationException(ErrorCode.MODERATION_DECISION_INVALID);
        }
        return normalized;
    }
}
