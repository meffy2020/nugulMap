package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneReviewService {

    private static final int MAX_REVIEW_LENGTH = 500;

    private final ZoneRepository zoneRepository;
    private final ZoneReviewRepository zoneReviewRepository;

    @Transactional(readOnly = true)
    public List<ZoneReviewResponse> getReviews(Integer zoneId) {
        ensureZoneExists(zoneId);
        return zoneReviewRepository.findByZoneIdWithAuthorOrderByCreatedAtDesc(zoneId)
                .stream()
                .map(ZoneReviewResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    @Transactional
    public ZoneReviewResponse createReview(Integer zoneId, ZoneReviewRequest request, User currentUser) {
        if (currentUser == null) {
            throw new BusinessBaseException(ErrorCode.ZONE_ACCESS_DENIED, "로그인 후 리뷰를 작성할 수 있습니다.");
        }

        String normalizedContent = request != null && request.getContent() != null
                ? request.getContent().trim()
                : "";

        if (normalizedContent.isEmpty()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "리뷰 내용을 입력해주세요.");
        }

        if (normalizedContent.length() > MAX_REVIEW_LENGTH) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "리뷰는 500자 이하로 입력해주세요.");
        }

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));

        try {
            ZoneReview savedReview = zoneReviewRepository.save(
                    ZoneReview.builder()
                            .zone(zone)
                            .author(currentUser)
                            .content(normalizedContent)
                            .build()
            );
            return ZoneReviewResponse.from(savedReview);
        } catch (Exception e) {
            log.error("Zone review 저장 실패: {}", e.getMessage(), e);
            throw new BusinessBaseException(ErrorCode.DATABASE_ERROR, "리뷰 저장 중 오류가 발생했습니다.");
        }
    }

    private void ensureZoneExists(Integer zoneId) {
        if (!zoneRepository.existsById(zoneId)) {
            throw new NotFoundException(ErrorCode.ZONE_NOT_FOUND);
        }
    }
}
