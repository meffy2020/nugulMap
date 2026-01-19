package com.neogulmap.neogul_map.config.security.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 인증 실패 처리 핸들러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {
    
    private final OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;
    
    @Value("${app.frontend-url:http://localhost}")
    private String frontendUrl;
    
    @Value("${app.oauth2.failure-redirect-url:}")
    private String failureRedirectUrl;
    
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     AuthenticationException exception) throws IOException, ServletException {
        
        log.error("OAuth2 인증 실패: {}", exception.getMessage(), exception);
        
        // 인가 요청 쿠키 정리
        authorizationRequestRepository.removeAuthorizationRequest(request, response);
        
        // REST API 요청인지 확인 (Accept 헤더가 application/json인 경우)
        String acceptHeader = request.getHeader("Accept");
        boolean isJsonRequest = acceptHeader != null && acceptHeader.contains("application/json");
        
        if (isJsonRequest) {
            // REST API 요청: JSON 응답 반환
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"OAuth2 로그인에 실패했습니다.\",\"error\":\"%s\"}",
                exception.getMessage() != null ? exception.getMessage().replace("\"", "\\\"") : "oauth_failed"
            ));
            return;
        }
        
        // 브라우저 요청: 리다이렉트 처리
        String errorMessage = exception.getMessage() != null 
            ? java.net.URLEncoder.encode(exception.getMessage(), "UTF-8") 
            : "oauth_failed";
        
        // 리다이렉트 URL 결정
        // 우선순위 1: 상대 경로 사용 (Nginx 80포트 유지를 위해 최우선)
        // localhost:3000이 포함되어 있거나 비어있으면 무조건 상대경로로 가게 함
        String redirectUrl;
        if (failureRedirectUrl == null || failureRedirectUrl.isEmpty() || failureRedirectUrl.contains("localhost:3000")) {
            // 상대 경로 사용 (Nginx 헤더 기반) - 80포트 유지
            redirectUrl = "/test/oauth2/failure?error=" + errorMessage;
        } else {
            // 환경 변수로 설정된 경우
            if (failureRedirectUrl.contains("/test")) {
                // 테스트 모드: TestController로 리다이렉트 (상대 경로)
                redirectUrl = "/test/oauth2/failure?error=" + errorMessage;
            } else {
                // 운영 환경에서 명시적인 외부 URL이 설정된 경우에만 사용
                redirectUrl = failureRedirectUrl + (failureRedirectUrl.contains("?") ? "&" : "?") + "error=" + errorMessage;
            }
        }
        
        log.info("OAuth2 인증 실패 리다이렉트 - redirectUrl: {}, frontendUrl: {}", redirectUrl, frontendUrl);
        
        // 상대 경로를 사용하면 Spring Boot가 X-Forwarded 헤더를 기반으로 자동으로 올바른 도메인/포트로 변환
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}

