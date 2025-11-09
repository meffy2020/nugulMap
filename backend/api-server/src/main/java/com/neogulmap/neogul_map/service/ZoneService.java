package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private static final double EARTH_RADIUS_KM = 6371.0;
    
    private final ZoneRepository zoneRepository;
    private final ImageService imageService;

    @Transactional
    public ZoneResponse createZone(ZoneRequest request, MultipartFile image) {
        try {
            // 이미지 처리 (ImageService 사용)
            if (image != null && !image.isEmpty()) {
                String imageFileName = imageService.processImage(image, ImageType.ZONE);
                request.setImage(imageFileName);
            }

            Zone zone = request.toEntity();
            Zone savedZone = zoneRepository.save(zone);
            
            return ZoneResponse.from(savedZone);
            
        } catch (DataIntegrityViolationException e) {
            // DB 유니크 제약조건 위반 시 중복 주소로 처리
            if (e.getMessage().contains("address") || e.getMessage().contains("unique")) {
                throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS, e);
            }
            throw new BusinessBaseException(ErrorCode.ZONE_SAVE_DATABASE_ERROR, e);
        } catch (IllegalArgumentException e) {
            log.error("Zone 생성 중 잘못된 인수 오류: {}", e.getMessage(), e);
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "Zone 생성 중 잘못된 인수가 전달되었습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("Zone 생성 실패: {}", e.getMessage(), e);
            throw new BusinessBaseException(ErrorCode.ZONE_SAVE_DATABASE_ERROR, e);
        }
    }

    @Transactional(readOnly = true)
    public ZoneResponse getZone(Integer zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
        return ZoneResponse.from(zone);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAllZones() {
        return zoneRepository.findAll().stream()
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }
    
    @Transactional(readOnly = true)
    public Page<ZoneResponse> getAllZones(Pageable pageable) {
        return zoneRepository.findAll(pageable)
                .map(ZoneResponse::from);
    }
    
    // 간단한 키워드 검색 (MVP 수준)
    @Transactional(readOnly = true)
    public List<ZoneResponse> searchZones(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllZones();
        }
        
        // 간단한 주소 검색
        return zoneRepository.findByAddressContainingIgnoreCase(keyword)
                .stream()
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
        log.info("반경 검색 시작 - 중심점: ({}, {}), 반경: {}m", latitude, longitude, radius);
        
        // 모든 Zone을 가져와서 거리 계산 (소규모 데이터용)
        // 실제 운영에서는 DB의 공간 인덱스나 GIS 함수 사용 권장
        List<Zone> allZones = zoneRepository.findAll();
        
        List<ZoneResponse> nearbyZones = allZones.stream()
                .filter(zone -> {
                    double zoneLat = zone.getLatitude().doubleValue();
                    double zoneLon = zone.getLongitude().doubleValue();
                    double distance = calculateDistance(latitude, longitude, zoneLat, zoneLon);
                    boolean isNearby = distance <= radius;
                    
                    if (isNearby) {
                        log.debug("Zone '{}' 거리: {}m (반경: {}m)", zone.getAddress(), String.format("%.2f", distance), radius);
                    }
                    
                    return isNearby;
                })
                .map(ZoneResponse::from)
                .collect(Collectors.toUnmodifiableList());
        
        log.info("반경 검색 완료 - 총 {}개 Zone 발견", nearbyZones.size());
        return nearbyZones;
    }

    /**
     * 두 지점 간의 거리 계산 (Haversine 공식)
     * 
     * @param lat1 첫 번째 지점의 위도
     * @param lon1 첫 번째 지점의 경도
     * @param lat2 두 번째 지점의 위도
     * @param lon2 두 번째 지점의 경도
     * @return 거리 (미터)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c * 1000; // 미터로 변환
        
        return distance;
    }

    @Transactional
    public ZoneResponse updateZone(Integer zoneId, ZoneRequest request, MultipartFile image) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));

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
        
        try {
            return ZoneResponse.from(zone);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage().contains("address") || e.getMessage().contains("unique")) {
                throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS, e);
            }
            throw new BusinessBaseException(ErrorCode.ZONE_SAVE_DATABASE_ERROR, e);
        } catch (Exception e) {
            log.error("Zone 업데이트 실패: {}", e.getMessage(), e);
            throw new BusinessBaseException(ErrorCode.ZONE_SAVE_DATABASE_ERROR, e);
        }
    }

    @Transactional
    public void deleteZone(Integer zoneId) {
        try {
            Zone zone = zoneRepository.findById(zoneId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
            
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
}