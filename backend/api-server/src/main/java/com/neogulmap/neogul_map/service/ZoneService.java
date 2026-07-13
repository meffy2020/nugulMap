package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.domain.enums.ZonePublicationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneService {

    private static final int DEFAULT_PUBLIC_LIST_LIMIT = 100;
    private static final int MAX_PUBLIC_LIST_LIMIT = 200;
    private static final int DEFAULT_BOUNDS_LIMIT = 200;
    private static final int MAX_BOUNDS_LIMIT = 500;
    private static final int MAX_SEARCH_KEYWORD_LENGTH = 100;
    private static final int MAX_RADIUS_METERS = 50_000;
    private static final double MAX_BOUNDS_SPAN_DEGREES = 5.0;
    private static final double MAX_BOUNDS_AREA_SQUARE_DEGREES = 4.0;
    
    private final ZoneRepository zoneRepository;
    private final ImageService imageService;
    private final ReviewContentPolicy contentPolicy;

    @Transactional
    public ZoneResponse createZone(ZoneRequest request, MultipartFile image, User creator) {
        contentPolicy.ensureAllowed(request.getDescription());
        contentPolicy.ensureAllowed(request.getAddress());
        try {
            // 이미지 처리 (ImageService 사용)
            if (image != null && !image.isEmpty()) {
                String imageFileName = imageService.processImage(image, ImageType.ZONE);
                request.setImage(imageFileName);
            }

            Zone zone = request.toEntity();
            // date 설정 (null이면 현재 날짜)
            if (zone.getDate() == null) {
                zone.setDate(java.time.LocalDate.now());
            }
            // creator 설정 (User FK 관계)
            zone.setCreator(creator);
            // 앱 사용자가 등록한 장소와 이미지는 운영자 확인 전까지 공개 목록에 노출하지 않습니다.
            zone.setPublicationStatus(creator == null
                    ? ZonePublicationStatus.PUBLISHED
                    : ZonePublicationStatus.PENDING);
            // 하위 호환성을 위해 user 필드도 설정 (deprecated)
            if (creator != null && creator.getEmail() != null) {
                zone.setUser(creator.getEmail());
            }
            
            Zone savedZone = zoneRepository.save(zone);
            return ZoneResponse.from(savedZone);
            
        } catch (DataIntegrityViolationException e) {
            log.warn("Zone creation failed due to a data integrity violation");
            throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS, e);
        } catch (Exception e) {
            log.error("Zone 생성 실패: {}", e.getMessage(), e);
            throw new BusinessBaseException(ErrorCode.ZONE_SAVE_DATABASE_ERROR, e);
        }
    }

    @Transactional(readOnly = true)
    public ZoneResponse getZone(Integer zoneId) {
        Zone zone = zoneRepository.findByIdAndPublicationStatus(zoneId, ZonePublicationStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
        return ZoneResponse.from(zone);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAllZones() {
        return getAllZones(DEFAULT_PUBLIC_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAllZones(int limit) {
        int safeLimit = validateLimit(limit, MAX_PUBLIC_LIST_LIMIT, "limit");
        return zoneRepository.findByPublicationStatusOrderByDateAscIdAsc(
                        ZonePublicationStatus.PUBLISHED,
                        PageRequest.of(0, safeLimit)
                ).stream()
                .filter(this::isPublished)
                .limit(safeLimit)
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }
    
    @Transactional(readOnly = true)
    public Page<ZoneResponse> getAllZones(Pageable pageable) {
        Pageable safePageable = boundedPageable(pageable);
        return zoneRepository.findAllByPublicationStatus(ZonePublicationStatus.PUBLISHED, safePageable)
                .map(ZoneResponse::from);
    }
    
    // 키워드 검색 (위경도 있으면 거리순 정렬)
    @Transactional(readOnly = true)
    public List<ZoneResponse> searchZones(String keyword, Double lat, Double lng) {
        return searchZones(keyword, lat, lng, DEFAULT_PUBLIC_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> searchZones(String keyword, Double lat, Double lng, int limit) {
        int safeLimit = validateLimit(limit, MAX_PUBLIC_LIST_LIMIT, "limit");
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return getAllZones(safeLimit);
        }
        if (normalizedKeyword.length() > MAX_SEARCH_KEYWORD_LENGTH) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "검색어는 " + MAX_SEARCH_KEYWORD_LENGTH + "자 이하여야 합니다."
            );
        }
        validateOptionalLocation(lat, lng);
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.ASC, "id"));

        if (lat != null) {
            return zoneRepository.findByKeywordOrderByDistance(
                            normalizedKeyword,
                            lat,
                            lng,
                            ZonePublicationStatus.PUBLISHED,
                            pageable
                    )
                    .stream()
                    .map(obj -> (Zone) obj[0])
                    .filter(this::isPublished)
                    .map(ZoneResponse::from)
                    .collect(Collectors.toUnmodifiableList());
        }
        
        return zoneRepository.findByKeyword(
                        normalizedKeyword,
                        ZonePublicationStatus.PUBLISHED,
                        pageable
                )
                .stream()
                .filter(this::isPublished)
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 특정 사용자가 등록한 Zone 목록 조회 (이메일 기반 - 하위 호환성)
     * @param userEmail 사용자 이메일
     * @return 해당 사용자가 등록한 Zone 목록
     * @deprecated 이메일 기반 조회는 하위 호환성을 위해 유지
     * 새로운 코드에서는 getZonesByUserId(Long userId)를 사용하세요.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<ZoneResponse> getZonesByUser(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            return List.of();
        }
        
        return zoneRepository.findByUserContainingIgnoreCase(userEmail)
                .stream()
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 특정 사용자가 등록한 Zone 목록 조회 (PK 기반 - 권장)
     * JOIN FETCH를 사용하여 N+1 문제를 방지합니다.
     * 
     * @param userId 사용자 ID
     * @return 해당 사용자가 등록한 Zone 목록
     */
    @Transactional(readOnly = true)
    public List<ZoneResponse> getZonesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        // JOIN FETCH를 사용하여 creator 정보를 함께 가져옴 (N+1 문제 방지)
        return zoneRepository.findByCreatorIdWithCreator(userId)
                .stream()
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 경계 박스 검색 (뷰포트 기반)
     * 
     * @param minLat 남서쪽 위도
     * @param maxLat 북동쪽 위도
     * @param minLng 남서쪽 경도
     * @param maxLng 북동쪽 경도
     * @return 영역 내 Zone 목록
     */
    @Transactional(readOnly = true)
    public List<ZoneResponse> getZonesByBounds(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return getZonesByBounds(minLat, maxLat, minLng, maxLng, DEFAULT_BOUNDS_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getZonesByBounds(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng,
            int limit
    ) {
        validateBounds(minLat, maxLat, minLng, maxLng);
        int safeLimit = validateLimit(limit, MAX_BOUNDS_LIMIT, "limit");
        double latitudeSpan = maxLat - minLat;
        double longitudeSpan = maxLng - minLng;
        log.debug(
                "경계 박스 검색 시작 - latitudeSpan: {}, longitudeSpan: {}, limit: {}",
                latitudeSpan,
                longitudeSpan,
                safeLimit
        );

        return zoneRepository.findByLocationBounds(
                        minLat,
                        maxLat,
                        minLng,
                        maxLng,
                        ZonePublicationStatus.PUBLISHED,
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.ASC, "id"))
                )
                .stream()
                .filter(this::isPublished)
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 반경 검색 (위치 기반)
     * 
     * @param latitude 중심점 위도
     * @param longitude 중심점 경도
     * @param radius 반경 (미터)
     * @return 반경 내 Zone 목록
     */
    @Transactional(readOnly = true)
    public List<ZoneResponse> searchZonesByRadius(double latitude, double longitude, int radius) {
        return searchZonesByRadius(latitude, longitude, radius, DEFAULT_BOUNDS_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> searchZonesByRadius(double latitude, double longitude, int radius, int limit) {
        int safeLimit = validateLimit(limit, MAX_BOUNDS_LIMIT, "limit");
        validateLocation(latitude, longitude);
        if (radius < 1 || radius > MAX_RADIUS_METERS) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "검색 반경은 1m 이상 50km 이하여야 합니다."
            );
        }
        log.debug("반경 검색 시작 - 반경: {}m, limit: {}", radius, safeLimit);
        
        List<ZoneResponse> nearbyZones = zoneRepository.findNearbyZones(
                        latitude,
                        longitude,
                        radius / 1000.0,
                        ZonePublicationStatus.PUBLISHED,
                        PageRequest.of(0, safeLimit)
                ).stream()
                .filter(this::isPublished)
                .limit(safeLimit)
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
        
        log.debug("반경 검색 완료 - 총 {}개 Zone 발견", nearbyZones.size());
        return nearbyZones;
    }

    @Transactional
    public ZoneResponse updateZone(Integer zoneId, ZoneRequest request, MultipartFile image, User currentUser) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
        validateZoneOwner(zone, currentUser);
        contentPolicy.ensureAllowed(request.getDescription());
        contentPolicy.ensureAllowed(request.getAddress());

        // 이미지 처리 (ImageService 사용)
        if (image != null && !image.isEmpty()) {
            String imageFileName = imageService.processImage(image, ImageType.ZONE);
            request.setImage(imageFileName);
        } else if (request.getImage() == null || request.getImage().isEmpty()) {
            // 이미지가 null이거나 비어있으면 기존 이미지 유지 또는 삭제
            // 여기서는 기존 이미지를 유지하는 것으로 가정 (요청에 따라 변경 가능)
            request.setImage(zone.getImage());
        }

        zone.update(request);
        zone.setPublicationStatus(ZonePublicationStatus.PENDING);
        // creator는 변경하지 않음 (생성자 변경 불가)
        
        try {
            return ZoneResponse.from(zone);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS, e);
        } catch (Exception e) {
            log.error("Zone 업데이트 실패: {}", e.getMessage(), e);
            throw new BusinessBaseException(ErrorCode.ZONE_SAVE_DATABASE_ERROR, e);
        }
    }

    @Transactional
    public void deleteZone(Integer zoneId, User currentUser) {
        try {
            Zone zone = zoneRepository.findById(zoneId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
            validateZoneOwner(zone, currentUser);
            
            // 이미지 파일도 삭제
            if (zone.getImage() != null && !zone.getImage().isEmpty()) {
                imageService.deleteImage(zone.getImage(), ImageType.ZONE);
            }
            
            zoneRepository.deleteById(zoneId);
        } catch (NotFoundException e) {
            throw e; // 이미 정의된 예외는 그대로 전파
        } catch (Exception e) {
            log.error("Zone 삭제 실패: {}", e.getMessage(), e);
            throw new BusinessBaseException(ErrorCode.ZONE_DELETE_DATABASE_ERROR, e);
        }
    }

    private void validateZoneOwner(Zone zone, User currentUser) {
        if (currentUser == null) {
            throw new BusinessBaseException(ErrorCode.ZONE_ACCESS_DENIED);
        }

        boolean matchesCreatorId =
                zone.getCreator() != null
                        && zone.getCreator().getId() != null
                        && zone.getCreator().getId().equals(currentUser.getId());

        boolean matchesLegacyUser =
                zone.getUser() != null
                        && currentUser.getEmail() != null
                        && zone.getUser().equalsIgnoreCase(currentUser.getEmail());

        if (!matchesCreatorId && !matchesLegacyUser) {
            throw new BusinessBaseException(ErrorCode.ZONE_ACCESS_DENIED);
        }
    }

    private boolean isPublished(Zone zone) {
        return zone != null && zone.getPublicationStatus() == ZonePublicationStatus.PUBLISHED;
    }

    private void validateBounds(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        if (!isFinite(minLat) || !isFinite(maxLat) || !isFinite(minLng) || !isFinite(maxLng)) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "지도 영역 좌표가 올바르지 않습니다.");
        }
        if (minLat < -90.0 || maxLat > 90.0 || minLng < -180.0 || maxLng > 180.0) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "지도 영역 좌표가 허용 범위를 벗어났습니다.");
        }
        if (minLat >= maxLat || minLng >= maxLng) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "지도 영역의 최소 좌표는 최대 좌표보다 작아야 합니다.");
        }

        double latitudeSpan = maxLat - minLat;
        double longitudeSpan = maxLng - minLng;
        double area = latitudeSpan * longitudeSpan;
        if (latitudeSpan > MAX_BOUNDS_SPAN_DEGREES
                || longitudeSpan > MAX_BOUNDS_SPAN_DEGREES
                || area > MAX_BOUNDS_AREA_SQUARE_DEGREES) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "지도 영역이 너무 큽니다. 지도를 확대해 주세요.");
        }
    }

    private void validateOptionalLocation(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "위도와 경도를 함께 입력해 주세요."
            );
        }
        if (latitude != null) {
            validateLocation(latitude, longitude);
        }
    }

    private void validateLocation(Double latitude, Double longitude) {
        if (!isFinite(latitude) || !isFinite(longitude)
                || latitude < -90.0 || latitude > 90.0
                || longitude < -180.0 || longitude > 180.0) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "위치 좌표가 올바르지 않습니다.");
        }
    }

    private int validateLimit(int limit, int maxLimit, String fieldName) {
        if (limit < 1 || limit > maxLimit) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    fieldName + "은 1 이상 " + maxLimit + " 이하여야 합니다."
            );
        }
        return limit;
    }

    private Pageable boundedPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PUBLIC_LIST_LIMIT, Sort.by(Sort.Direction.ASC, "id"));
        }
        int boundedSize = Math.min(Math.max(1, pageable.getPageSize()), MAX_PUBLIC_LIST_LIMIT);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.ASC, "id");
        return PageRequest.of(Math.max(0, pageable.getPageNumber()), boundedSize, sort);
    }

    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
