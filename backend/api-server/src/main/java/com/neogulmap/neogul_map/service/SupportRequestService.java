package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.SupportRequest;
import com.neogulmap.neogul_map.domain.enums.SupportRequestCategory;
import com.neogulmap.neogul_map.domain.enums.SupportRequestStatus;
import com.neogulmap.neogul_map.dto.SupportRequestCreateRequest;
import com.neogulmap.neogul_map.dto.SupportRequestOperatorResponse;
import com.neogulmap.neogul_map.dto.SupportRequestResponse;
import com.neogulmap.neogul_map.dto.SupportRequestStatusUpdateRequest;
import com.neogulmap.neogul_map.repository.SupportRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SupportRequestService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_MESSAGE_LENGTH = 1_000;
    private static final long GLOBAL_REQUESTS_PER_MINUTE = 60;
    private static final Duration SAME_EMAIL_COOLDOWN = Duration.ofMinutes(10);

    private final SupportRequestRepository repository;
    private final Clock clock;

    @Value("${app.support.closed-request-retention-days:30}")
    private long closedRequestRetentionDays = 30;

    @Autowired
    public SupportRequestService(SupportRequestRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SupportRequestService(SupportRequestRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SupportRequestResponse create(SupportRequestCreateRequest request) {
        validateHoneypot(request);
        SupportRequestCategory category = parseCategory(request == null ? null : request.category());
        String email = normalizeEmail(request == null ? null : request.email());
        String message = normalizeMessage(request == null ? null : request.message());

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (repository.countByCreatedAtAfter(now.minusMinutes(1)) >= GLOBAL_REQUESTS_PER_MINUTE) {
            throw new BusinessBaseException(ErrorCode.SUPPORT_RATE_LIMITED, "잠시 후 다시 요청해주세요.");
        }
        if (repository.existsByEmailAndCategoryAndCreatedAtAfter(
                email,
                category,
                now.minus(SAME_EMAIL_COOLDOWN)
        )) {
            throw new BusinessBaseException(
                    ErrorCode.SUPPORT_RATE_LIMITED,
                    "같은 유형의 요청이 이미 접수되었습니다. 잠시 후 다시 시도해주세요."
            );
        }

        SupportRequest saved = repository.save(SupportRequest.builder()
                .category(category)
                .email(email)
                .message(message)
                .status(SupportRequestStatus.PENDING)
                .createdAt(now)
                .build());
        return SupportRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SupportRequestOperatorResponse> getActiveRequests() {
        return repository.findTop100ByStatusInOrderByCreatedAtAsc(List.of(
                        SupportRequestStatus.PENDING,
                        SupportRequestStatus.IN_PROGRESS
                ))
                .stream()
                .map(SupportRequestOperatorResponse::from)
                .toList();
    }

    @Transactional
    public SupportRequestOperatorResponse updateStatus(
            Long requestId,
            SupportRequestStatusUpdateRequest request
    ) {
        SupportRequest supportRequest = repository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SUPPORT_REQUEST_NOT_FOUND));
        SupportRequestStatus nextStatus = parseOperatorStatus(request == null ? null : request.status());
        SupportRequestStatus currentStatus = supportRequest.getStatus();

        if (currentStatus == nextStatus) {
            return SupportRequestOperatorResponse.from(supportRequest);
        }
        if (currentStatus == SupportRequestStatus.RESOLVED || currentStatus == SupportRequestStatus.REJECTED) {
            throw new ValidationException(ErrorCode.SUPPORT_STATUS_INVALID, "완료된 지원 요청은 다시 열 수 없습니다.");
        }

        supportRequest.setStatus(nextStatus);
        if (nextStatus == SupportRequestStatus.RESOLVED || nextStatus == SupportRequestStatus.REJECTED) {
            supportRequest.setResolvedAt(now());
        } else {
            supportRequest.setResolvedAt(null);
        }
        return SupportRequestOperatorResponse.from(repository.save(supportRequest));
    }

    @Scheduled(cron = "${app.support.closed-request-purge-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public long purgeExpiredClosedRequests() {
        long retentionDays = Math.max(1, closedRequestRetentionDays);
        return repository.deleteByStatusInAndResolvedAtBefore(
                List.of(SupportRequestStatus.RESOLVED, SupportRequestStatus.REJECTED),
                now().minusDays(retentionDays)
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void validateHoneypot(SupportRequestCreateRequest request) {
        if (request == null || (request.website() != null && !request.website().isBlank())) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다.");
        }
    }

    private SupportRequestCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "문의 유형을 선택해주세요.");
        }
        try {
            return SupportRequestCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 문의 유형입니다.");
        }
    }

    private SupportRequestStatus parseOperatorStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "처리 상태를 입력해주세요.");
        }
        try {
            SupportRequestStatus status = SupportRequestStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status == SupportRequestStatus.PENDING) {
                throw new ValidationException(ErrorCode.SUPPORT_STATUS_INVALID, "PENDING 상태로 되돌릴 수 없습니다.");
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(ErrorCode.SUPPORT_STATUS_INVALID, "지원하지 않는 처리 상태입니다.");
        }
    }

    private String normalizeEmail(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.INVALID_FORMAT, "회신받을 이메일 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeMessage(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "문의 내용을 입력해주세요.");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "문의 내용은 1000자 이하로 입력해주세요.");
        }
        return normalized;
    }
}
