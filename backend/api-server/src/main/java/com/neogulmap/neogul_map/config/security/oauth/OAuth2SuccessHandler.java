package com.neogulmap.neogul_map.config.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private final TokenProvider tokenProvider;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     Authentication authentication) throws IOException, ServletException {
        
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        try {
            // OAuth 사용자 정보로 User 객체 생성 또는 조회
            User user = createOrUpdateUser(oAuth2User);
            
            // JWT 토큰 생성 (간단한 버전)
            String accessToken = tokenProvider.generateToken(user, Duration.ofHours(2));
            String refreshToken = tokenProvider.generateToken(user, Duration.ofDays(30));
            
            // 응답 설정
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            
            // 성공 응답 생성
            Map<String, Object> responseBody = Map.of(
                "success", true,
                "message", "OAuth 로그인 성공",
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "nickname", user.getNickname()
                )
            );
            
            // JSON 응답 전송
            objectMapper.writeValue(response.getWriter(), responseBody);
            
            log.info("OAuth 로그인 성공: {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("OAuth 로그인 처리 중 오류 발생", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"로그인 처리 중 오류가 발생했습니다.\"}");
        }
    }
    
    private User createOrUpdateUser(OAuth2User oAuth2User) {
        // CustomOAuth2User로 캐스팅
        OAuth2UserCustomService.CustomOAuth2User customOAuth2User = 
            (OAuth2UserCustomService.CustomOAuth2User) oAuth2User;
        
        String email = customOAuth2User.getEmail();
        String nickname = customOAuth2User.getNickname();
        String profileImage = customOAuth2User.getProfileImage();
        String oauthId = customOAuth2User.getName();
        String oauthProvider = customOAuth2User.getRegistrationId();
        
        if (email == null) {
            throw new RuntimeException("OAuth2에서 이메일을 가져올 수 없습니다.");
        }
        
        // 기존 사용자 조회
        return userService.getUserByEmail(email)
                .map(existingUser -> {
                    // 기존 사용자 정보 업데이트
                    existingUser.setNickname(nickname != null ? nickname : existingUser.getNickname());
                    existingUser.setProfileImage(profileImage != null ? profileImage : existingUser.getProfileImage());
                    existingUser.setOauthId(oauthId);
                    existingUser.setOauthProvider(oauthProvider);
                    userService.updateUser(existingUser.getId(), convertToUserRequest(existingUser));
                    return existingUser;
                })
                .orElseGet(() -> {
                    // 새 사용자 생성
                    User newUser = User.builder()
                            .email(email)
                            .nickname(nickname != null ? nickname : email.split("@")[0])
                            .profileImage(profileImage)
                            .oauthId(oauthId)
                            .oauthProvider(oauthProvider)
                            .build();
                    
                    UserResponse userResponse = userService.createUser(convertToUserRequest(newUser));
                    return userService.getUser(userResponse.getId());
                });
    }
    
    private com.neogulmap.neogul_map.dto.UserRequest convertToUserRequest(User user) {
        return com.neogulmap.neogul_map.dto.UserRequest.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .oauthId(user.getOauthId())
                .oauthProvider(user.getOauthProvider())
                .build();
    }
}
