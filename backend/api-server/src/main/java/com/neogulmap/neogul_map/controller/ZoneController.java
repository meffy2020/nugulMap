package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.service.ZoneService;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/zones")
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping
    public ResponseEntity<ZoneResponse> createZone(@RequestPart(value = "image", required = false) MultipartFile image,
                                                 @RequestPart("data") String zoneData) throws IOException {
        // 1차 검증: Zone 데이터 검증
        if (zoneData == null || zoneData.trim().isEmpty()) {
            throw new RuntimeException("Zone 데이터가 필요합니다");
        }
        
        // JSON 데이터를 ZoneRequest로 파싱
        ZoneRequest request = parseZoneRequest(zoneData);
        
        // 1차 검증: 이미지 기본 검증
        if (image != null && !image.isEmpty()) {
            validateImage(image);
        }
        
        // 서비스에서 이미지 처리와 함께 Zone 생성
        ZoneResponse response = zoneService.createZone(request, image);
        return ResponseEntity.status(201) // 201 Created
                .header("X-Message", "흡연구역 생성 성공")
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponse> getZone(@PathVariable("id") Integer id) {
        ZoneResponse response = zoneService.getZone(id);
        return ResponseEntity.ok()
                .header("X-Message", "흡연구역 조회 성공")
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> getAllZones() {
        List<ZoneResponse> response = zoneService.getAllZones();
        return ResponseEntity.ok()
                .header("X-Message", "모든 흡연구역 조회 성공")
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponse> updateZone(@PathVariable("id") Integer id,
                                                 @RequestPart(value = "image", required = false) MultipartFile image,
                                                 @RequestPart("data") String zoneData) throws IOException {
        // 1차 검증: Zone 데이터 검증
        if (zoneData == null || zoneData.trim().isEmpty()) {
            throw new RuntimeException("Zone 데이터가 필요합니다");
        }
        
        // JSON 데이터를 ZoneRequest로 파싱
        ZoneRequest request = parseZoneRequest(zoneData);
        
        // 1차 검증: 이미지 기본 검증
        if (image != null && !image.isEmpty()) {
            validateImage(image);
        }
        
        // 서비스에서 이미지 처리와 함께 Zone 업데이트
        ZoneResponse response = zoneService.updateZone(id, request, image);
        return ResponseEntity.ok()
                .header("X-Message", "흡연구역 업데이트 성공")
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable("id") Integer id) {
        zoneService.deleteZone(id);
        return ResponseEntity.ok()
                .header("X-Message", "흡연구역 삭제 성공")
                .build();
    }
    
    // JSON 문자열을 ZoneRequest로 파싱하는 헬퍼 메서드
    private ZoneRequest parseZoneRequest(String zoneData) {
        try {
            // Jackson ObjectMapper를 사용하여 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(zoneData, ZoneRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("흡연구역 데이터 파싱 실패: " + e.getMessage());
        }
    }
    
    // 이미지 1차 검증 메서드
    private void validateImage(MultipartFile image) {
        // 파일 크기 검사 (10MB)
        if (image.getSize() > 10 * 1024 * 1024) {
            throw new ProfileImageProcessingException("이미지 크기는 10MB 이하여야 합니다");
        }
        
        // 파일 타입 검사
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ProfileImageProcessingException("이미지 파일만 업로드 가능합니다");
        }
        
        // 파일 확장자 검사 - 더 정확한 검증
        String originalFilename = image.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.toLowerCase();
            // 파일명에 확장자가 있는지 확인
            if (!extension.contains(".")) {
                throw new ProfileImageProcessingException("파일 확장자가 필요합니다");
            }
            
            // 지원되는 확장자 검사
            if (!extension.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
                throw new ProfileImageProcessingException("지원하지 않는 이미지 형식입니다. (.jpg, .jpeg, .png, .gif, .webp만 가능)");
            }
        } else {
            throw new ProfileImageProcessingException("파일명이 필요합니다");
        }
        
        log.info("이미지 검증 성공: {} (크기: {} bytes, 타입: {})", 
                originalFilename, image.getSize(), contentType);
    }
    
    // 이미지 파일 처리하는 헬퍼 메서드
    private String processImage(MultipartFile image) throws IOException {
        // 파일 유효성 검사
        if (image.isEmpty()) {
            throw new ProfileImageRequiredException();
        }
        
        // 파일 크기 검사 (10MB)
        if (image.getSize() > 10 * 1024 * 1024) {
            throw new ProfileImageProcessingException("이미지 크기는 10MB 이하여야 합니다");
        }
        
        // 파일 타입 검사
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ProfileImageProcessingException("이미지 파일만 업로드 가능합니다");
        }
        
        // 파일명 생성
        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = "zone_" + timestamp + "_" + uniqueId + extension;
        
        // 파일 저장 - 절대 경로 사용
        Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads", "zones");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        Path filePath = uploadPath.resolve(fileName);
        image.transferTo(filePath.toFile());
        
        log.info("Zone 이미지 업로드 성공: {}", fileName);
        return fileName;
    }
}
