package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.service.ZoneService;
import com.neogulmap.neogul_map.service.ImageService;
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

    @PostMapping
    public ResponseEntity<?> createZone(@RequestPart(value = "image", required = false) MultipartFile image,
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
        ZoneResponse response = zoneService.createZone(request, image);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "success", true,
                    "message", "흡연구역 생성 성공",
                    "data", Map.of(
                        "zone", response,
                        "image", image != null ? "uploaded" : "none"
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

    @GetMapping
    public ResponseEntity<?> getAllZones() {
        List<ZoneResponse> response = zoneService.getAllZones();
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

    // 반경 검색 (위치 기반)
    @GetMapping(params = {"latitude", "longitude", "radius"})
    public ResponseEntity<?> getZonesByRadius(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam("radius") int radius) {
        List<ZoneResponse> response = zoneService.searchZonesByRadius(latitude, longitude, radius);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", String.format("반경 %dm 내 흡연구역 조회 성공", radius),
            "data", Map.of(
                "zones", response,
                "count", response.size()
            )
        ));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateZone(@PathVariable("id") Integer id,
                                       @RequestPart(value = "image", required = false) MultipartFile image,
                                       @RequestPart("data") String zoneData) {
        // 1차 검증: Zone 데이터 검증
        if (zoneData == null || zoneData.trim().isEmpty()) {
            throw new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, "Zone 데이터가 필요합니다");
        }
        
        // JSON 데이터를 ZoneRequest로 파싱
        ZoneRequest request = parseZoneRequest(zoneData);
        
        // 서비스에서 이미지 처리와 함께 Zone 업데이트
        ZoneResponse response = zoneService.updateZone(id, request, image);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "흡연구역 업데이트 성공",
            "data", Map.of(
                "zone", response,
                "image", image != null ? "updated" : "unchanged"
            )
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteZone(@PathVariable("id") Integer id) {
        zoneService.deleteZone(id);
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
            throw new RuntimeException("Zone 데이터 파싱 실패: " + e.getMessage());
        }
    }
    
}
