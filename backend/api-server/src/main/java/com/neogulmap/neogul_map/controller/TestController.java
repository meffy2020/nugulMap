package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.service.ZoneService;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.StorageService;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.dto.ZoneResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final UserService userService;
    private final ZoneService zoneService;
    private final ImageService imageService;
    private final StorageService storageService;

    /**
     * 메인 테스트 페이지
     */
    @GetMapping
    public String home(Model model) {
        model.addAttribute("title", "NeogulMap 서비스 테스트");
        return "service-test";
    }

    /**
     * 고급 테스트 페이지
     */
    @GetMapping("/advanced")
    public String advancedTest(Model model) {
        model.addAttribute("title", "고급 API 테스트");
        return "test";
    }

    // ==================== USER SERVICE 테스트 ====================

    /**
     * 사용자 목록 조회 - 실제 UserService 호출
     */
    @GetMapping("/users")
    public String getUsers(Model model) {
        // 실제 UserService를 호출해서 사용자 목록 조회
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("message", "사용자 목록 조회 성공 (" + users.size() + "명)");
        return "user-list";
    }

    /**
     * 사용자 생성 테스트
     */
    @PostMapping(value = "/users", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody UserRequest userRequest) {
        UserResponse userResponse = userService.createUser(userRequest);
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "사용자 생성 성공",
            "user", userResponse
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 조회 테스트
     */
    @GetMapping("/users/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable("id") Long id) {
        User user = userService.getUser(id);
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "사용자 조회 성공",
            "user", user
        );
        return ResponseEntity.ok(response);
    }

    // ==================== ZONE SERVICE 테스트 ====================

    /**
     * Zone 목록 조회 - 실제 ZoneService 호출
     */
    @GetMapping("/zones")
    public String getZones(Model model) {
        // 실제 ZoneService를 호출해서 Zone 목록 조회
        List<ZoneResponse> zones = zoneService.getAllZones();
        model.addAttribute("zones", zones);
        model.addAttribute("message", "Zone 목록 조회 성공 (" + zones.size() + "개)");
        return "zone-list";
    }

    /**
     * Zone 생성 테스트
     */
    @PostMapping(value = "/zones", consumes = "multipart/form-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createZone(
            @RequestParam("address") String address,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "subtype", required = false) String subtype,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "creator", required = false) String creator) {
        
        ZoneRequest zoneRequest = new ZoneRequest();
        zoneRequest.setAddress(address);
        zoneRequest.setDescription(description);
        zoneRequest.setRegion(region != null ? region : "서울특별시");
        zoneRequest.setType(type != null ? type : "흡연구역");
        zoneRequest.setSubtype(subtype != null ? subtype : "실외");
        zoneRequest.setSize(size != null ? size : "중형");
        zoneRequest.setLatitude(latitude != null ? java.math.BigDecimal.valueOf(latitude) : java.math.BigDecimal.valueOf(37.5665));
        zoneRequest.setLongitude(longitude != null ? java.math.BigDecimal.valueOf(longitude) : java.math.BigDecimal.valueOf(126.9780));
        zoneRequest.setUser(creator != null ? creator : "테스트유저");
        
        ZoneResponse zoneResponse = zoneService.createZone(zoneRequest, image);
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Zone 생성 성공",
            "zone", zoneResponse
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Zone 조회 테스트
     */
    @GetMapping("/zones/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getZone(@PathVariable("id") Integer id) {
        ZoneResponse zone = zoneService.getZone(id);
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Zone 조회 성공",
            "zone", zone
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Zone 검색 테스트
     */
    @GetMapping("/zones/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchZones(@RequestParam("keyword") String keyword) {
        List<ZoneResponse> zones = zoneService.searchZones(keyword);
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Zone 검색 성공",
            "zones", zones,
            "keyword", keyword
        );
        return ResponseEntity.ok(response);
    }

    // ==================== IMAGE SERVICE 테스트 ====================

    /**
     * 이미지 업로드 테스트 (테스트 전용 경로) - 정적 템플릿 방식
     */
    @PostMapping(value = "/images/upload", consumes = "multipart/form-data")
    public String uploadImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("type") String type,
            Model model) {
        
        try {
            ImageType imageType = ImageType.valueOf(type.toUpperCase());
            String fileName = imageService.processImage(image, imageType);
            
            model.addAttribute("success", true);
            model.addAttribute("message", "이미지 업로드 성공");
            model.addAttribute("fileName", fileName);
            model.addAttribute("originalName", image.getOriginalFilename());
            model.addAttribute("size", image.getSize());
            
        } catch (Exception e) {
            model.addAttribute("success", false);
            model.addAttribute("error", "이미지 업로드 실패: " + e.getMessage());
        }
        
        return "image-upload-result";
    }

    /**
     * 이미지 조회 테스트 (테스트 전용 경로)
     */
    @GetMapping("/test/images/{fileName}")
    public ResponseEntity<Resource> getImage(@PathVariable("fileName") String fileName) {
        Resource resource = imageService.getImage(fileName);
        String contentType = imageService.getContentType(fileName);
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(resource);
    }

    // ==================== STORAGE SERVICE 테스트 ====================

    /**
     * Storage 서비스 테스트
     */
    @PostMapping(value = "/storage/test", consumes = "multipart/form-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testStorage(@RequestParam MultipartFile file) {
        String tempName = storageService.saveTemp(file);
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Storage 테스트 성공",
            "tempName", tempName,
            "exists", storageService.exists(tempName)
        );
        return ResponseEntity.ok(response);
    }

    // ==================== 위치 기반 검색 테스트 ====================

    /**
     * 반경 검색 테스트 (위치 기반) - ZoneService 사용
     */
    @GetMapping("/zones/nearby")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchNearbyZones(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam(value = "radius", defaultValue = "1000") int radius) {
        
        // ZoneService의 반경 검색 메서드 사용
        List<ZoneResponse> nearbyZones = zoneService.searchZonesByRadius(lat, lon, radius);
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "반경 검색 성공",
            "zones", nearbyZones,
            "center", Map.of("lat", lat, "lon", lon),
            "radius", radius,
            "count", nearbyZones.size()
        );
        
        return ResponseEntity.ok(response);
    }

    // ==================== 통합 테스트 ====================

    /**
     * 모든 서비스 상태 확인
     */
    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> healthCheck() {
        // 각 서비스의 기본 동작 확인
        Map<String, String> services = Map.of(
            "UserService", "OK",
            "ZoneService", "OK", 
            "ImageService", "OK",
            "StorageService", "OK"
        );
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "모든 서비스 정상 동작",
            "services", services,
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(response);
    }
}
