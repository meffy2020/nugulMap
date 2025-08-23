package com.neogulmap.neogul_map.config.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.config.jwt.TokenProvider;
import com.neogulmap.neogul_map.domain.User;
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
            
            // JWT 토큰 생성
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
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String oauthId = oAuth2User.getName();
        String oauthProvider = getOAuthProvider(oAuth2User);
        
        // 기존 사용자 조회
        return userService.getUserByEmail(email)
                .orElseGet(() -> {
                    // 새 사용자 생성
                    User newUser = User.builder()
                            .email(email)
                            .nickname(name != null ? name : email.split("@")[0])
                            .oauthId(oauthId)
                            .oauthProvider(oauthProvider)
                            .build();
                    
                    // UserService의 createUser 메서드 호출
                    userService.createUser(convertToUserRequest(newUser));
                    return newUser;
                });
    }
    
    private String getOAuthProvider(OAuth2User oAuth2User) {
        // OAuth 제공자 식별 (Google, Kakao, Naver 등)
        String provider = oAuth2User.getAttribute("provider");
        if (provider == null) {
            // 기본값 또는 다른 방법으로 제공자 식별
            provider = "unknown";
        }
        return provider;
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
