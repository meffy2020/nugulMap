package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneReportRequest;
import com.neogulmap.neogul_map.dto.ZoneReportResponse;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.service.ZoneModerationService;
import com.neogulmap.neogul_map.service.ZoneService;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageUploadException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/zones")
public class ZoneController {

    private final ZoneService zoneService;
    private final ImageService imageService;
    private final ZoneModerationService zoneModerationService;

    @PostMapping
    public ResponseEntity<?> createZone(@CurrentUser User creator,
                                       @RequestPart(value = "image", required = false) MultipartFile image,
                                       @RequestPart("data") String zoneData) {
        // 1차 검증: Zone 데이터 검증
        if (zoneData == null || zoneData.trim().isEmpty()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "Zone 데이터가 필요합니다");
        }
        
        // JSON 데이터를 ZoneRequest로 파싱
        ZoneRequest request = parseZoneRequest(zoneData);
        
        // MVP 수준 입력 검증
        if (!ValidationUtil.isValidZoneAddress(request.getAddress())) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "Zone 주소는 5자 이상이어야 합니다");
        }
        
        if (!ValidationUtil.isValidLatitude(request.getLatitude())) {
            throw new ValidationException(ErrorCode.LOCATION_LATITUDE_INVALID, "유효하지 않은 위도입니다");
        }
        
        if (!ValidationUtil.isValidLongitude(request.getLongitude())) {
            throw new ValidationException(ErrorCode.LOCATION_LONGITUDE_INVALID, "유효하지 않은 경도입니다");
        }
        
        // 서비스에서 이미지 처리와 함께 Zone 생성
        ZoneResponse response = zoneService.createZone(request, image, creator);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "success", true,
                    "message", "흡연구역 등록이 접수되었습니다.",
                    "data", Map.of(
                        "zone", response,
                        "image", image != null ? "uploaded" : "none"
                    )
                ));
    }

    @GetMapping("/bounds")
    public ResponseEntity<?> getZonesByBounds(
            @RequestParam("minLat") Double minLat,
            @RequestParam("maxLat") Double maxLat,
            @RequestParam("minLng") Double minLng,
            @RequestParam("maxLng") Double maxLng,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        
        List<ZoneResponse> response = zoneService.getZonesByBounds(minLat, maxLat, minLng, maxLng, limit);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "영역 내 흡연구역 조회 성공",
            "data", Map.of(
                "zones", response,
                "count", response.size()
            )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getZone(@PathVariable("id") Integer id) {
        ZoneResponse response = zoneService.getZone(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "흡연구역 조회 성공",
            "data", Map.of("zone", response)
        ));
    }

    @PostMapping("/{id}/reports")
    public ResponseEntity<?> reportZone(@PathVariable("id") Integer id,
                                        @RequestBody ZoneReportRequest request,
                                        @CurrentUser User reporter) {
        ZoneReportResponse report = zoneModerationService.reportZone(id, request, reporter);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "장소 신고가 접수되었습니다.",
                "data", Map.of("report", report)
        ));
    }

    @GetMapping
    public ResponseEntity<?> getAllZones(
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radius", required = false) Double radius,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        
        List<ZoneResponse> response;
        
        boolean hasAnyRadiusParameter = latitude != null || longitude != null || radius != null;
        if (hasAnyRadiusParameter) {
            if (latitude == null || longitude == null || radius == null) {
                throw new ValidationException(
                        ErrorCode.VALIDATION_ERROR,
                        "반경 검색에는 위도, 경도, 반경이 모두 필요합니다."
                );
            }
            int radiusMeters = radiusKilometersToMeters(radius);
            response = zoneService.searchZonesByRadius(latitude, longitude, radiusMeters, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("반경 %.2fkm 내 흡연구역 조회 성공", radius),
                "data", Map.of(
                    "zones", response,
                    "count", response.size()
                )
            ));
        }
        
        // 파라미터가 없으면 모든 zones 반환
        response = zoneService.getAllZones(limit);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "모든 흡연구역 조회 성공",
            "data", Map.of(
                "zones", response,
                "count", response.size()
            )
        ));
    }
    
    // 모든 흡연구역 조회 (페이지네이션)
    @GetMapping("/paged")
    public ResponseEntity<?> getAllZonesPaged(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<ZoneResponse> response = zoneService.getAllZones(pageable);
        
        String message = String.format("흡연구역 조회 성공 (페이지: %d/%d, 총 %d개)", 
                response.getNumber() + 1, response.getTotalPages(), response.getTotalElements());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", message,
            "data", Map.of(
                "zones", response.getContent(),
                "pagination", Map.of(
                    "currentPage", response.getNumber(),
                    "totalPages", response.getTotalPages(),
                    "totalElements", response.getTotalElements(),
                    "size", response.getSize()
                )
            )
        ));
    }

    /**
     * 키워드로 흡연구역 검색
     * @param keyword 검색 키워드 (지역, 주소, 타입, 서브타입에서 검색)
     * @return 검색된 Zone 목록
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchZones(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<ZoneResponse> response = zoneService.searchZones(keyword, lat, lng, limit);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "흡연구역 검색 성공",
            "data", Map.of(
                "zones", response,
                "count", response.size(),
                "keyword", keyword
            )
        ));
    }

    /**
     * 현재 인증된 사용자가 등록한 흡연구역 목록 조회
     * @param user 현재 인증된 사용자 (@CurrentUser로 자동 주입)
     * @return 현재 사용자가 등록한 Zone 목록
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyZones(@CurrentUser User user) {
        // PK 기반으로 Zone 조회 (JOIN FETCH 사용, N+1 문제 방지)
        List<ZoneResponse> response = zoneService.getZonesByUserId(user.getId());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "내가 등록한 흡연구역 조회 성공",
            "data", Map.of(
                "zones", response,
                "count", response.size()
            )
        ));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateZone(@PathVariable("id") Integer id,
                                       @CurrentUser User user,
                                       @RequestPart(value = "image", required = false) MultipartFile image,
                                       @RequestPart("data") String zoneData) {
        // 1차 검증: Zone 데이터 검증
        if (zoneData == null || zoneData.trim().isEmpty()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "Zone 데이터가 필요합니다");
        }
        
        // JSON 데이터를 ZoneRequest로 파싱
        ZoneRequest request = parseZoneRequest(zoneData);
        
        // 서비스에서 이미지 처리와 함께 Zone 업데이트
        ZoneResponse response = zoneService.updateZone(id, request, image, user);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "흡연구역 수정 검토가 접수되었습니다.",
            "data", Map.of(
                "zone", response,
                "image", image != null ? "updated" : "unchanged"
            )
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteZone(@PathVariable("id") Integer id,
                                        @CurrentUser User user) {
        zoneService.deleteZone(id, user);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "흡연구역 삭제 성공",
            "data", Map.of("deletedZoneId", id)
        ));
    }
    
    // JSON 문자열을 ZoneRequest로 파싱하는 헬퍼 메서드
    private ZoneRequest parseZoneRequest(String zoneData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(zoneData, ZoneRequest.class);
        } catch (Exception e) {
            log.error("Zone 데이터 파싱 실패: {}", e.getMessage(), e);
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "흡연구역 정보 형식이 올바르지 않습니다.");
        }
    }

    private int radiusKilometersToMeters(double radiusKilometers) {
        if (!Double.isFinite(radiusKilometers) || radiusKilometers <= 0 || radiusKilometers > 50.0) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "검색 반경은 0km 초과 50km 이하여야 합니다."
            );
        }
        return (int) Math.round(radiusKilometers * 1000.0);
    }
    
}
