package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.LocationLetter;
import com.neogulmap.neogul_map.domain.LocationLetterLike;
import com.neogulmap.neogul_map.domain.LocationLetterReport;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import com.neogulmap.neogul_map.domain.enums.ReviewReportReason;
import com.neogulmap.neogul_map.dto.LocationLetterCreateRequest;
import com.neogulmap.neogul_map.dto.LocationLetterNearbyResponse;
import com.neogulmap.neogul_map.dto.LocationLetterLikeResponse;
import com.neogulmap.neogul_map.dto.LocationLetterReportResponse;
import com.neogulmap.neogul_map.dto.LocationLetterReportOperatorResponse;
import com.neogulmap.neogul_map.dto.LocationLetterResponse;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.dto.ReviewReportRequest;
import com.neogulmap.neogul_map.repository.LocationLetterReportRepository;
import com.neogulmap.neogul_map.repository.LocationLetterRepository;
import com.neogulmap.neogul_map.repository.LocationLetterLikeRepository;
import com.neogulmap.neogul_map.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class LocationLetterService {

    public static final int NEARBY_RADIUS_METERS = 180;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_REPORT_DETAILS_LENGTH = 500;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

    private final LocationLetterRepository letterRepository;
    private final LocationLetterReportRepository reportRepository;
    private final LocationLetterLikeRepository likeRepository;
    private final UserBlockRepository userBlockRepository;
    private final ReviewContentPolicy contentPolicy;

    @Transactional(readOnly = true)
    public LocationLetterNearbyResponse getNearby(
            Double latitude,
            Double longitude,
            Integer requestedLimit,
            User currentUser
    ) {
        Coordinates coordinates = requireCoordinates(latitude, longitude);
        int limit = requestedLimit == null
                ? DEFAULT_LIMIT
                : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        double latitudeDelta = NEARBY_RADIUS_METERS / METERS_PER_LATITUDE_DEGREE;
        double longitudeScale = Math.max(Math.cos(Math.toRadians(coordinates.latitude())), 0.01);
        double longitudeDelta = NEARBY_RADIUS_METERS / (METERS_PER_LATITUDE_DEGREE * longitudeScale);
        int candidateLimit = Math.min(limit * 6, 300);

        Set<Long> blockedAuthorIds = currentUser == null || currentUser.getId() == null
                ? Set.of()
                : userBlockRepository.findBlockedUserIdsByBlockerId(currentUser.getId());
        List<LocationLetter> nearbyLetters = letterRepository.findNearbyCandidates(
                        decimal(coordinates.latitude() - latitudeDelta, 6),
                        decimal(coordinates.latitude() + latitudeDelta, 6),
                        decimal(coordinates.longitude() - longitudeDelta, 6),
                        decimal(coordinates.longitude() + longitudeDelta, 6),
                        PageRequest.of(0, candidateLimit)
                ).stream()
                .filter(letter -> letter.getAuthor() == null
                        || !blockedAuthorIds.contains(letter.getAuthor().getId()))
                .filter(letter -> distanceMeters(
                        coordinates.latitude(),
                        coordinates.longitude(),
                        letter.getLatitudeBucket().doubleValue(),
                        letter.getLongitudeBucket().doubleValue()
                ) <= NEARBY_RADIUS_METERS)
                .limit(limit)
                .toList();

        List<Long> letterIds = nearbyLetters.stream().map(LocationLetter::getId).toList();
        Map<Long, Long> likeCounts = loadLikeCounts(letterIds);
        Set<Long> likedLetterIds = currentUser == null || currentUser.getId() == null || letterIds.isEmpty()
                ? Set.of()
                : new HashSet<>(likeRepository.findLikedLetterIds(currentUser.getId(), letterIds));
        List<LocationLetterResponse> letters = nearbyLetters.stream()
                .map(letter -> LocationLetterResponse.from(
                        letter,
                        currentUser,
                        likeCounts.getOrDefault(letter.getId(), 0L),
                        likedLetterIds.contains(letter.getId())
                ))
                .toList();

        return new LocationLetterNearbyResponse(letters, letters.size(), NEARBY_RADIUS_METERS);
    }

    @Transactional
    public LocationLetterResponse create(LocationLetterCreateRequest request, User currentUser) {
        requireAuthenticated(currentUser);
        if (request == null) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "편지 내용을 입력해주세요.");
        }
        Coordinates coordinates = requireCoordinates(request.latitude(), request.longitude());
        String content = normalizeContent(request.content());
        contentPolicy.ensureAllowed(content);

        LocationLetter letter = letterRepository.save(LocationLetter.builder()
                .author(currentUser)
                .latitudeBucket(decimal(coordinates.latitude(), 4))
                .longitudeBucket(decimal(coordinates.longitude(), 4))
                .content(content)
                .build());
        return LocationLetterResponse.from(letter, currentUser, 0, false);
    }

    @Transactional
    public LocationLetterLikeResponse like(Long letterId, User currentUser) {
        requireAuthenticated(currentUser);
        LocationLetter letter = letterRepository.findByIdWithAuthor(letterId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LETTER_NOT_FOUND));
        if (!likeRepository.existsByLetterIdAndUserId(letterId, currentUser.getId())) {
            likeRepository.save(LocationLetterLike.builder()
                    .letter(letter)
                    .user(currentUser)
                    .build());
        }
        return new LocationLetterLikeResponse(letterId, likeRepository.countByLetterId(letterId), true);
    }

    @Transactional
    public LocationLetterLikeResponse unlike(Long letterId, User currentUser) {
        requireAuthenticated(currentUser);
        letterRepository.findByIdWithAuthor(letterId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LETTER_NOT_FOUND));
        likeRepository.deleteByLetterIdAndUserId(letterId, currentUser.getId());
        return new LocationLetterLikeResponse(letterId, likeRepository.countByLetterId(letterId), false);
    }

    @Transactional
    public void delete(Long letterId, User currentUser) {
        requireAuthenticated(currentUser);
        LocationLetter letter = letterRepository.findByIdWithAuthor(letterId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LETTER_NOT_FOUND));
        if (letter.getAuthor() == null || !currentUser.getId().equals(letter.getAuthor().getId())) {
            throw new BusinessBaseException(ErrorCode.LETTER_ACCESS_DENIED);
        }
        letterRepository.delete(letter);
    }

    @Transactional
    public LocationLetterReportResponse report(
            Long letterId,
            ReviewReportRequest request,
            User reporter
    ) {
        requireAuthenticated(reporter);
        LocationLetter letter = letterRepository.findByIdWithAuthor(letterId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LETTER_NOT_FOUND));
        if (reportRepository.existsByLetterIdAndReporterId(letterId, reporter.getId())) {
            throw new BusinessBaseException(ErrorCode.LETTER_DUPLICATE_REPORT);
        }

        ReviewReportReason reason = parseReason(request == null ? null : request.reason());
        String details = normalizeReportDetails(request == null ? null : request.details());
        if (reason == ReviewReportReason.OTHER && details == null) {
            throw new ValidationException(
                    ErrorCode.REQUIRED_FIELD_MISSING,
                    "기타 신고 사유의 상세 내용을 입력해주세요."
            );
        }
        LocationLetterReport report = reportRepository.save(LocationLetterReport.builder()
                .letter(letter)
                .reporter(reporter)
                .reason(reason)
                .details(details)
                .status(ModerationStatus.PENDING)
                .build());
        return LocationLetterReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<LocationLetterReportOperatorResponse> getPendingReports() {
        return reportRepository.findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus.PENDING)
                .stream()
                .map(LocationLetterReportOperatorResponse::from)
                .toList();
    }

    @Transactional
    public ModerationDecisionResponse decideReport(Long reportId, ModerationDecisionRequest request) {
        LocationLetterReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MODERATION_REPORT_NOT_FOUND));
        if (report.getStatus() != ModerationStatus.PENDING) {
            throw new ValidationException(ErrorCode.MODERATION_DECISION_INVALID, "이미 처리된 신고입니다.");
        }

        String action = parseDecisionAction(request == null ? null : request.action());
        if ("REMOVE_CONTENT".equals(action)) {
            LocationLetter letter = report.getLetter();
            Long letterId = letter.getId();
            reportRepository.deleteByLetterId(letterId);
            letterRepository.delete(letter);
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
        report.setResolvedAt(LocalDateTime.now(ZoneOffset.UTC));
        reportRepository.save(report);
        return new ModerationDecisionResponse(reportId, action, status.name(), false);
    }

    private Coordinates requireCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new ValidationException(ErrorCode.LOCATION_COORDINATES_INVALID);
        }
        if (latitude < -90 || latitude > 90) {
            throw new ValidationException(ErrorCode.LOCATION_LATITUDE_INVALID);
        }
        if (longitude < -180 || longitude > 180) {
            throw new ValidationException(ErrorCode.LOCATION_LONGITUDE_INVALID);
        }
        return new Coordinates(latitude, longitude);
    }

    private String normalizeContent(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "편지 내용을 입력해주세요.");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "편지는 500자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private void requireAuthenticated(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessBaseException(ErrorCode.LETTER_ACCESS_DENIED, "편지를 남기려면 로그인이 필요합니다.");
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

    private String normalizeReportDetails(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REPORT_DETAILS_LENGTH) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "신고 상세 내용은 500자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private String parseDecisionAction(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "처리 결정을 선택해주세요.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("RESOLVE")
                && !normalized.equals("DISMISS")
                && !normalized.equals("REMOVE_CONTENT")) {
            throw new ValidationException(ErrorCode.MODERATION_DECISION_INVALID, "지원하지 않는 처리 결정입니다.");
        }
        return normalized;
    }

    private Map<Long, Long> loadLikeCounts(List<Long> letterIds) {
        if (letterIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : likeRepository.countByLetterIds(letterIds)) {
            if (row.length >= 2 && row[0] instanceof Number letterId && row[1] instanceof Number count) {
                counts.put(letterId.longValue(), count.longValue());
            }
        }
        return counts;
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6_371_000.0;
        double latitudeDistance = Math.toRadians(lat2 - lat1);
        double longitudeDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record Coordinates(double latitude, double longitude) {
    }
}
