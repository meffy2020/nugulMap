package com.neogulmap.neogul_map.config.security.oauth;

import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private final TokenProvider tokenProvider;
    private final UserService userService;
    
    @Value("${app.oauth2.success-redirect-url:http://localhost:3000}")
    private String successRedirectUrl;
    
    @Value("${app.oauth2.signup-redirect-url:http://localhost:3000/signup}")
    private String signupRedirectUrl;
    
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;
    
    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;
    
    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final int ACCESS_TOKEN_MAX_AGE = 2 * 60 * 60; // 2시간 (초)
    private static final int REFRESH_TOKEN_MAX_AGE = 30 * 24 * 60 * 60; // 30일 (초)
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     Authentication authentication) throws IOException, ServletException {
        
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        try {
            // OAuth 사용자 정보로 User 객체 생성 또는 조회 (UserService에서 처리)
            OAuth2UserCustomService.CustomOAuth2User customOAuth2User = 
                (OAuth2UserCustomService.CustomOAuth2User) oAuth2User;
            
            log.info("OAuth2 로그인 처리 시작 - Email: {}, Provider: {}", 
                customOAuth2User.getEmail(), customOAuth2User.getRegistrationId());
            
            User user = userService.processOAuth2User(customOAuth2User);
            
            log.info("사용자 처리 완료 - User ID: {}, Email: {}, Nickname: {}, DB 저장 완료", 
                user.getId(), user.getEmail(), user.getNickname());
            
            // 프로필 완료 여부 확인
            boolean isProfileComplete = user.isProfileComplete();
            
            // === 디버깅 시작 ===
            log.info("=== 디버깅 시작 ===");
            log.info("유저 이메일: {}", user.getEmail());
            log.info("유저 닉네임: {}", user.getNickname());
            log.info("닉네임 null 여부: {}", user.getNickname() == null);
            log.info("닉네임 빈 문자열 여부: {}", user.getNickname() != null && user.getNickname().trim().isEmpty());
            log.info("프로필 완료 여부(isProfileComplete): {}", isProfileComplete);
            log.info("최종 이동할 URL: {}", isProfileComplete ? successRedirectUrl : signupRedirectUrl + "?email=" + user.getEmail());
            log.info("=== 디버깅 종료 ===");
            
            log.info("프로필 완료 여부 확인 - isProfileComplete: {}, Nickname: {}", 
                isProfileComplete, user.getNickname());
            
            // JWT 토큰 생성
            String accessToken = tokenProvider.generateToken(user, Duration.ofHours(2));
            String refreshToken = tokenProvider.generateToken(user, Duration.ofDays(30));
            
            log.info("JWT 토큰 생성 완료 - AccessToken 길이: {}, RefreshToken 길이: {}", 
                accessToken.length(), refreshToken.length());
            
            // HttpOnly 쿠키에 토큰 저장 (XSS 공격 방지)
            addHttpOnlyCookie(response, ACCESS_TOKEN_COOKIE_NAME, accessToken, ACCESS_TOKEN_MAX_AGE);
            addHttpOnlyCookie(response, REFRESH_TOKEN_COOKIE_NAME, refreshToken, REFRESH_TOKEN_MAX_AGE);
            
            log.info("쿠키 설정 완료 - AccessToken: {}, RefreshToken: {}", 
                ACCESS_TOKEN_COOKIE_NAME, REFRESH_TOKEN_COOKIE_NAME);
            
            // REST API 요청인지 확인 (Accept 헤더가 application/json인 경우)
            String acceptHeader = request.getHeader("Accept");
            boolean isJsonRequest = acceptHeader != null && acceptHeader.contains("application/json");
            
            if (isJsonRequest) {
                // REST API 요청: JSON 응답 반환
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(String.format(
                    "{\"success\":true,\"message\":\"OAuth2 로그인이 성공적으로 완료되었습니다.\",\"requiresSignup\":%s,\"user\":{\"id\":%d,\"email\":\"%s\",\"nickname\":\"%s\"}}",
                    !isProfileComplete,
                    user.getId(),
                    user.getEmail() != null ? user.getEmail().replace("\"", "\\\"") : "",
                    user.getNickname() != null ? user.getNickname().replace("\"", "\\\"") : ""
                ));
                return;
            }
            
            // 브라우저 요청: 리다이렉트 처리
            String redirectUrl;
            
            // 요청 출처 확인 (Referer 헤더 또는 Origin 헤더)
            String referer = request.getHeader("Referer");
            String origin = request.getHeader("Origin");
            boolean isFromBackendLogin = (referer != null && referer.contains("localhost:8080/login")) ||
                                       (origin != null && origin.contains("localhost:8080"));
            
            log.info("요청 출처 확인 - Referer: {}, Origin: {}, isFromBackendLogin: {}", 
                referer, origin, isFromBackendLogin);
            
            if (!isProfileComplete) {
                // 프로필이 완료되지 않은 경우
                if (isFromBackendLogin) {
                    // 백엔드 /login에서 온 경우 → 백엔드 /signup (Thymeleaf 템플릿)
                    redirectUrl = "/auth/signup?email=" + java.net.URLEncoder.encode(user.getEmail(), "UTF-8");
                    log.info("프로필 미완료 - 백엔드 회원가입 페이지로 리다이렉트: {}", redirectUrl);
                } else {
                    // 프론트엔드에서 온 경우 → 프론트엔드 /signup (Next.js)
                    redirectUrl = signupRedirectUrl + "?email=" + java.net.URLEncoder.encode(user.getEmail(), "UTF-8");
                    log.info("프로필 미완료 - 프론트엔드 회원가입 페이지로 리다이렉트: {}", redirectUrl);
                }
            } else {
                // 프로필이 완료된 경우 성공 페이지로 리다이렉트
                if (isFromBackendLogin) {
                    // 백엔드에서 온 경우 → 테스트 페이지 또는 메인
                    redirectUrl = "/test";
                } else {
                    // 프론트엔드에서 온 경우 → 프론트엔드 메인 페이지
                    redirectUrl = successRedirectUrl;
                    if (successRedirectUrl == null || successRedirectUrl.equals("http://localhost:3000")) {
                        redirectUrl = "http://localhost:3000";
                    } else if (successRedirectUrl.contains("/test")) {
                        redirectUrl = "/test/oauth2/success";
                    }
                }
            }
            
            // 프런트엔드 또는 테스트 페이지로 리다이렉트
            log.info("리다이렉트 결정 - redirectUrl: {}, isProfileComplete: {}, successRedirectUrl: {}", 
                redirectUrl, isProfileComplete, successRedirectUrl);
            
            response.sendRedirect(redirectUrl);
            
            log.info("OAuth 로그인 성공 (HttpOnly Cookie): {}, 프로필 완료: {}, 리다이렉트: {}", 
                user.getEmail(), isProfileComplete, redirectUrl);
            
        } catch (Exception e) {
            log.error("OAuth 로그인 처리 중 오류 발생", e);
            log.error("예외 상세 정보 - 예외 타입: {}, 메시지: {}", e.getClass().getName(), e.getMessage());
            e.printStackTrace();
            // 실패 시 실패 페이지로 리다이렉트 (메인으로 가는 것을 방지)
            String failureUrl = "/test/oauth2/failure?error=login_failed";
            log.error("실패 URL로 리다이렉트: {}", failureUrl);
            response.sendRedirect(failureUrl);
        }
    }
    
    /**
     * HttpOnly, Secure, SameSite 설정이 적용된 쿠키 추가
     * 
     * 주의: response.addCookie()와 response.setHeader("Set-Cookie", ...)를 함께 사용하면
     * 쿠키가 중복 설정되거나 덮어씌워질 수 있습니다.
     * 따라서 Set-Cookie 헤더를 직접 설정하는 방식만 사용합니다.
     */
    private void addHttpOnlyCookie(HttpServletResponse response, String name, String value, int maxAge) {
        // SameSite 설정을 위해 Set-Cookie 헤더 직접 설정
        // Java Cookie API는 SameSite를 직접 지원하지 않으므로 헤더로 설정
        String cookieHeader;
        if ("None".equals(cookieSameSite) && cookieSecure) {
            // SameSite=None은 Secure가 필수
            cookieHeader = String.format("%s=%s; Path=/; HttpOnly; Secure; Max-Age=%d; SameSite=None", 
                name, value, maxAge);
        } else if ("Strict".equals(cookieSameSite)) {
            cookieHeader = String.format("%s=%s; Path=/; HttpOnly; %sMax-Age=%d; SameSite=Strict", 
                name, value, cookieSecure ? "Secure; " : "", maxAge);
        } else {
            // Lax (기본값) - 리다이렉트 후에도 쿠키 전달 가능
            cookieHeader = String.format("%s=%s; Path=/; HttpOnly; %sMax-Age=%d; SameSite=Lax", 
                name, value, cookieSecure ? "Secure; " : "", maxAge);
        }
        
        // Set-Cookie 헤더 직접 설정 (SameSite 포함)
        // addHeader를 사용하여 여러 쿠키를 설정할 수 있도록 함
        response.addHeader("Set-Cookie", cookieHeader);
        
        log.info("쿠키 설정 완료 - Name: {}, Path: /, HttpOnly: true, Secure: {}, SameSite: {}, Max-Age: {}", 
            name, cookieSecure, cookieSameSite, maxAge);
    }
}
