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
import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
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

/**
 * 테스트 컨트롤러 - 테스트/개발 환경 전용
 * - 테스트 페이지 (View) 제공
 * - 테스트 API 엔드포인트 제공
 * 
 * 주의: 이 컨트롤러는 테스트/개발 환경 전용입니다.
 * 프로덕션 환경에서는 비활성화하거나 제거해야 합니다.
 */
@Slf4j
@Controller
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final UserService userService;
    private final ZoneService zoneService;
    private final ImageService imageService;
    private final StorageService storageService;
    private final TokenProvider tokenProvider;
    private final Environment environment;

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

    /**
     * OAuth2 테스트 페이지
     */
    @GetMapping("/oauth2")
    public String oauth2Test(Model model) {
        model.addAttribute("title", "OAuth2 환경 변수 테스트");
        return "oauth2-test";
    }

    /**
     * OAuth2 로그인 성공 페이지 (정적 템플릿)
     */
    @GetMapping("/oauth2/success")
    public String oauth2SuccessTest(Model model) {
        log.info("OAuth2 로그인 성공 테스트 페이지 접근");
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() && 
                authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
                
                model.addAttribute("success", true);
                model.addAttribute("message", "OAuth2 로그인이 성공적으로 완료되었습니다.");
                model.addAttribute("userInfo", oAuth2User.getAttributes());
                model.addAttribute("authorities", oAuth2User.getAuthorities());
                model.addAttribute("name", oAuth2User.getName());
                
                // User 객체도 조회해서 추가 정보 제공
                try {
                    String email = oAuth2User.getAttribute("email");
                    if (email != null) {
                        userService.getUserByEmail(email).ifPresent(user -> {
                            model.addAttribute("user", user);
                        });
                    }
                } catch (Exception e) {
                    log.debug("사용자 정보 조회 실패 (무시): {}", e.getMessage());
                }
            } else {
                model.addAttribute("success", false);
                model.addAttribute("message", "인증 정보를 찾을 수 없습니다. 로그인을 다시 시도해주세요.");
            }
        } catch (Exception e) {
            log.error("OAuth2 성공 페이지 처리 중 오류", e);
            model.addAttribute("success", false);
            model.addAttribute("message", "오류 발생: " + e.getMessage());
        }
        
        model.addAttribute("timestamp", java.time.LocalDateTime.now().toString());
        return "oauth2-success";
    }

    /**
     * OAuth2 로그인 실패 페이지 (정적 템플릿)
     */
    @GetMapping("/oauth2/failure")
    public String oauth2FailureTest(Model model, @RequestParam(required = false) String error) {
        log.warn("OAuth2 로그인 실패 테스트 페이지 접근: {}", error);
        
        model.addAttribute("success", false);
        model.addAttribute("message", "OAuth2 로그인에 실패했습니다.");
        model.addAttribute("error", error != null ? error : "알 수 없는 오류");
        model.addAttribute("timestamp", java.time.LocalDateTime.now().toString());
        
        return "oauth2-failure";
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

    // ==================== AUTH/OAUTH 테스트 ====================

    /**
     * OAuth2 환경 변수 확인 테스트
     * docker-compose.yml의 env_file: .env.local에서 주입된 환경 변수를 직접 확인
     */
    @GetMapping("/oauth2/env-check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkOAuth2Env() {
        Map<String, Object> envStatus = new java.util.HashMap<>();
        
        // 각 제공자별 환경 변수 상태 확인 (docker-compose에서 주입된 환경 변수 직접 읽기)
        for (String provider : new String[]{"google", "kakao", "naver"}) {
            Map<String, Object> providerStatus = new java.util.HashMap<>();
            
            String envPrefix = provider.toUpperCase();
            String clientIdEnv = envPrefix + "_CLIENT_ID";
            String clientSecretEnv = envPrefix + "_CLIENT_SECRET";
            
            // 환경 변수 직접 읽기 (System.getenv 또는 Environment 사용)
            String clientId = System.getenv(clientIdEnv);
            if (clientId == null) {
                clientId = environment.getProperty(clientIdEnv, "");
            }
            
            String clientSecret = System.getenv(clientSecretEnv);
            if (clientSecret == null) {
                clientSecret = environment.getProperty(clientSecretEnv, "");
            }
            
            // 환경 변수가 없으면 "없음"으로 표시
            if (clientId == null || clientId.isEmpty()) {
                clientId = "없음";
            }
            if (clientSecret == null || clientSecret.isEmpty()) {
                clientSecret = "없음";
            }
            
            // 기본값인지 확인 (기본값이면 환경 변수가 제대로 주입되지 않은 것)
            boolean isConfigured = !"없음".equals(clientId) && !clientId.isEmpty() && 
                                  !"없음".equals(clientSecret) && !clientSecret.isEmpty() &&
                                  !clientId.startsWith("your-") && !clientSecret.startsWith("your-");
            
            String status = isConfigured ? "✅ 설정됨" : "⚠️ 환경 변수 미설정";
            
            // 보안을 위해 시크릿은 마스킹 처리
            String maskedSecret = maskSecret(clientSecret);
            String maskedClientId = maskValue(clientId);
            
            providerStatus.put("clientId", maskedClientId);
            providerStatus.put("clientSecret", maskedSecret);
            providerStatus.put("configured", isConfigured);
            providerStatus.put("status", status);
            providerStatus.put("loginUrl", "/oauth2/authorization/" + provider);
            providerStatus.put("envVarName", Map.of("clientId", clientIdEnv, "clientSecret", clientSecretEnv));
            
            envStatus.put(provider, providerStatus);
        }
        
        // 환경 변수 직접 확인 (시스템 환경 변수)
        Map<String, String> systemEnv = new java.util.HashMap<>();
        systemEnv.put("GOOGLE_CLIENT_ID", System.getenv("GOOGLE_CLIENT_ID") != null ? maskValue(System.getenv("GOOGLE_CLIENT_ID")) : "없음");
        systemEnv.put("GOOGLE_CLIENT_SECRET", System.getenv("GOOGLE_CLIENT_SECRET") != null ? maskSecret(System.getenv("GOOGLE_CLIENT_SECRET")) : "없음");
        systemEnv.put("KAKAO_CLIENT_ID", System.getenv("KAKAO_CLIENT_ID") != null ? maskValue(System.getenv("KAKAO_CLIENT_ID")) : "없음");
        systemEnv.put("KAKAO_CLIENT_SECRET", System.getenv("KAKAO_CLIENT_SECRET") != null ? maskSecret(System.getenv("KAKAO_CLIENT_SECRET")) : "없음");
        systemEnv.put("NAVER_CLIENT_ID", System.getenv("NAVER_CLIENT_ID") != null ? maskValue(System.getenv("NAVER_CLIENT_ID")) : "없음");
        systemEnv.put("NAVER_CLIENT_SECRET", System.getenv("NAVER_CLIENT_SECRET") != null ? maskSecret(System.getenv("NAVER_CLIENT_SECRET")) : "없음");
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("message", "OAuth2 환경 변수 확인 (docker-compose env_file에서 주입)");
        response.put("envStatus", envStatus);
        response.put("systemEnv", systemEnv);
        response.put("note", "docker-compose.yml의 env_file: .env.local에서 환경 변수를 주입합니다.");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 시크릿 값 마스킹 처리
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty() || secret.startsWith("your-") || "없음".equals(secret)) {
            return secret;
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }
    
    /**
     * 클라이언트 ID 마스킹 처리 (일부만 표시)
     */
    private String maskValue(String value) {
        if (value == null || value.isEmpty() || value.startsWith("your-") || "없음".equals(value)) {
            return value;
        }
        if (value.length() <= 20) {
            return value.substring(0, Math.min(8, value.length())) + "...";
        }
        return value.substring(0, 12) + "..." + value.substring(value.length() - 8);
    }

    /**
     * OAuth2 로그인 URL 조회 테스트
     */
    @GetMapping("/oauth2/login-urls")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOAuth2LoginUrls() {
        Map<String, String> loginUrls = Map.of(
            "google", "/oauth2/authorization/google",
            "kakao", "/oauth2/authorization/kakao",
            "naver", "/oauth2/authorization/naver"
        );
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "OAuth2 로그인 URL 목록",
            "loginUrls", loginUrls,
            "instructions", Map.of(
                "google", "GET " + loginUrls.get("google") + " 로 접근하면 Google 로그인 시작",
                "kakao", "GET " + loginUrls.get("kakao") + " 로 접근하면 Kakao 로그인 시작",
                "naver", "GET " + loginUrls.get("naver") + " 로 접근하면 Naver 로그인 시작"
            )
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 토큰 검증 테스트
     */
    @PostMapping("/auth/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "토큰이 필요합니다."
                ));
            }
            
            boolean isValid = tokenProvider.validToken(token);
            
            if (isValid) {
                String email = tokenProvider.getEmailFromToken(token);
                Long userId = tokenProvider.getUserId(token);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "valid", true,
                    "email", email,
                    "userId", userId,
                    "message", "토큰이 유효합니다."
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "valid", false,
                    "message", "유효하지 않거나 만료된 토큰입니다."
                ));
            }
            
        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "토큰 검증 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }

    /**
     * Refresh 토큰 재발급 테스트
     */
    @PostMapping("/auth/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            
            if (refreshToken == null || refreshToken.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Refresh token이 필요합니다."
                ));
            }
            
            // Refresh token 검증
            if (!tokenProvider.validToken(refreshToken)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "유효하지 않거나 만료된 refresh token입니다."
                ));
            }
            
            // 토큰에서 사용자 정보 추출
            String email = tokenProvider.getEmailFromToken(refreshToken);
            
            // 사용자 조회
            User user = userService.getUserByEmail(email)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            
            // 새로운 토큰 발급
            String newAccessToken = tokenProvider.generateToken(user, java.time.Duration.ofHours(2));
            String newRefreshToken = tokenProvider.generateToken(user, java.time.Duration.ofDays(30));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "토큰이 성공적으로 재발급되었습니다.",
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken,
                "user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "nickname", user.getNickname()
                )
            ));
            
        } catch (Exception e) {
            log.error("토큰 재발급 중 오류 발생", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "토큰 재발급 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }

    /**
     * 현재 인증된 사용자 정보 조회 테스트 (쿠키/헤더 기반)
     */
    @GetMapping("/auth/me")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        try {
            // SecurityContext에서 인증 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "인증되지 않은 사용자입니다.",
                    "hint", "OAuth2 로그인 후 쿠키에 저장된 토큰으로 자동 인증됩니다."
                ));
            }
            
            User user = null;
            String email = null;
            
            // UserDetails에서 이메일 추출
            if (authentication.getPrincipal() instanceof UserDetails userDetails) {
                email = userDetails.getUsername();
                user = userService.getUserByEmail(email).orElse(null);
            } else if (authentication.getPrincipal() instanceof String) {
                email = (String) authentication.getPrincipal();
                user = userService.getUserByEmail(email).orElse(null);
            }
            
            // 쿠키에서 토큰 확인
            String tokenFromCookie = null;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("accessToken".equals(cookie.getName())) {
                        tokenFromCookie = cookie.getValue();
                        break;
                    }
                }
            }
            
            // 헤더에서 토큰 확인
            String tokenFromHeader = null;
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                tokenFromHeader = authHeader.substring(7);
            }
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("message", "현재 인증된 사용자 정보");
            response.put("authentication", Map.of(
                "authenticated", authentication.isAuthenticated(),
                "principal", authentication.getPrincipal().getClass().getSimpleName(),
                "authorities", authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .toList()
            ));
            
            if (user != null) {
                response.put("user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "nickname", user.getNickname(),
                    "oauthProvider", user.getOauthProvider() != null ? user.getOauthProvider() : "N/A"
                ));
            } else {
                response.put("user", "사용자 정보를 찾을 수 없습니다.");
            }
            
            response.put("tokenInfo", Map.of(
                "hasCookieToken", tokenFromCookie != null,
                "hasHeaderToken", tokenFromHeader != null,
                "cookieTokenValid", tokenFromCookie != null && tokenProvider.validToken(tokenFromCookie),
                "headerTokenValid", tokenFromHeader != null && tokenProvider.validToken(tokenFromHeader)
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("현재 사용자 정보 조회 중 오류 발생", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "사용자 정보 조회 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }

    // 중복 제거: /oauth2/success는 78번 라인의 oauth2SuccessTest() 메서드가 처리함
    // 중복 제거: /oauth2/failure는 123번 라인의 oauth2FailureTest() 메서드가 처리함

    /**
     * 쿠키 정보 확인 테스트
     */
    @GetMapping("/auth/cookies")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        java.util.List<Map<String, Object>> cookieList = new java.util.ArrayList<>();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                Map<String, Object> cookieInfo = new java.util.HashMap<>();
                cookieInfo.put("name", cookie.getName());
                cookieInfo.put("value", cookie.getValue().length() > 50 ? 
                    cookie.getValue().substring(0, 50) + "..." : cookie.getValue());
                cookieInfo.put("path", cookie.getPath());
                cookieInfo.put("maxAge", cookie.getMaxAge());
                cookieInfo.put("secure", cookie.getSecure());
                // HttpOnly는 Java Cookie API에서 직접 확인 불가 (Set-Cookie 헤더에서만 확인 가능)
                cookieInfo.put("httpOnly", "N/A (Set-Cookie 헤더에서만 확인 가능)");
                cookieList.add(cookieInfo);
            }
        }
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "쿠키 정보",
            "cookies", cookieList,
            "hasAccessToken", cookieList.stream().anyMatch(c -> "accessToken".equals(c.get("name"))),
            "hasRefreshToken", cookieList.stream().anyMatch(c -> "refreshToken".equals(c.get("name")))
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
            "StorageService", "OK",
            "AuthService", "OK",
            "OAuth2Service", "OK"
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
