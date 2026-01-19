package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.Map;

/**
 * 인증 관련 컨트롤러
 * - Refresh 토큰 재발급
 * - 회원가입 페이지 및 완료 처리
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    
    private final TokenProvider tokenProvider;
    private final UserService userService;
    private final ImageService imageService;
    
    /**
     * Refresh 토큰으로 새로운 Access Token과 Refresh Token 발급
     * 
     * @param refreshToken 기존 refresh token
     * @return 새로운 access token과 refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
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
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
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
            String newAccessToken = tokenProvider.generateToken(user, Duration.ofHours(2));
            String newRefreshToken = tokenProvider.generateToken(user, Duration.ofDays(30));
            
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "토큰 재발급 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 토큰 검증 엔드포인트
     * 
     * @param token 검증할 토큰
     * @return 토큰 유효성 결과
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
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
                    "userId", userId
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "토큰 검증 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 현재 인증된 사용자 정보 조회
     * 
     * @param user 현재 인증된 사용자 (@CurrentUser로 자동 주입)
     * @return 현재 사용자 정보
     */
    @GetMapping("/me")
    @ResponseBody
    public ResponseEntity<?> getCurrentUser(@CurrentUser User user) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "현재 인증된 사용자 정보",
            "data", Map.of(
                "user", UserResponse.from(user)
            )
        ));
    }
    
    /**
     * 회원가입 페이지 (GET) - OAuth 로그인 후 프로필 완성 폼 표시
     * OAuth2SuccessHandler에서 프로필 미완료 사용자를 이 페이지로 리다이렉트
     * Thymeleaf 템플릿을 반환합니다.
     */
    @GetMapping("/signup")
    public String signupPage(Model model, 
                             @RequestParam(value = "email", required = false) String email,
                             @CurrentUser(required = false) User currentUser) {
        log.info("회원가입 페이지 접근 - Email: {}", email);
        
        // 인증된 사용자가 있으면 이메일 사용
        if (currentUser != null && (email == null || email.isEmpty())) {
            email = currentUser.getEmail();
            log.info("인증된 사용자 - Email: {}", email);
        } else if (currentUser == null) {
            log.warn("인증되지 않은 사용자가 회원가입 페이지 접근 시도");
        }
        
        // 이메일이 없으면 URL 파라미터에서 가져오기 시도
        if (email == null || email.isEmpty()) {
            email = "";
        }
        
        model.addAttribute("email", email);
        return "signup";
    }
    
    /**
     * 회원가입 완료 API (프로필 사진과 닉네임 등록)
     * OAuth 로그인 후 첫 로그인 시 프로필 정보를 완성하는 엔드포인트
     * 
     * @param nickname 닉네임 (필수)
     * @param profileImage 프로필 이미지 (선택)
     * @return 완료된 사용자 정보
     */
    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> completeSignup(
            @CurrentUser User user,
            @RequestParam("nickname") String nickname,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage) {
        try {
            
            // 이미 프로필이 완료된 경우
            if (user.isProfileComplete()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "이미 회원가입이 완료된 사용자입니다."
                ));
            }
            
            // 닉네임 검증
            if (nickname == null || nickname.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "닉네임은 필수입니다."
                ));
            }
            
            if (nickname.length() < 2 || nickname.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "닉네임은 2자 이상 20자 이하여야 합니다."
                ));
            }
            
            // 프로필 이미지 처리
            String profileImagePath = null;
            if (profileImage != null && !profileImage.isEmpty()) {
                profileImagePath = imageService.processImage(profileImage, ImageType.PROFILE);
            }
            
            // 사용자 정보 업데이트
            UserRequest userRequest = UserRequest.builder()
                    .email(user.getEmail())
                    .oauthId(user.getOauthId())
                    .oauthProvider(user.getOauthProvider())
                    .nickname(nickname.trim())
                    .profileImage(profileImagePath)
                    .build();
            
            // 사용자 정보 업데이트
            userService.updateUser(user.getId(), userRequest);
            
            // 프로필 이미지가 있으면 별도로 업데이트
            if (profileImagePath != null) {
                userService.updateProfileImage(user.getId(), profileImagePath);
            }
            
            // 업데이트된 사용자 정보 조회
            User updatedUser = userService.getUser(user.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원가입이 완료되었습니다.",
                "data", Map.of(
                    "user", UserResponse.from(updatedUser)
                )
            ));
            
        } catch (Exception e) {
            log.error("회원가입 완료 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "회원가입 완료 처리 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
}

