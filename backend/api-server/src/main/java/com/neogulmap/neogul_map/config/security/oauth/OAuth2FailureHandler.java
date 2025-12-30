package com.neogulmap.neogul_map.config.security.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 인증 실패 처리 핸들러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler implements AuthenticationFailureHandler {
    
    private final OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;
    
    @Value("${app.oauth2.failure-redirect-url:http://localhost:3000/login?error=oauth_failed}")
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
        
        // 리다이렉트 URL 결정 (테스트 모드 체크)
        String redirectUrl;
        if (failureRedirectUrl == null || 
            failureRedirectUrl.equals("http://localhost:3000/login?error=oauth_failed") || 
            failureRedirectUrl.contains("/test")) {
            // 테스트 모드: TestController로 리다이렉트
            redirectUrl = "/test/oauth2/failure?error=" + errorMessage;
        } else {
            // 프로덕션 모드: 프런트엔드로 리다이렉트
            redirectUrl = failureRedirectUrl + (failureRedirectUrl.contains("?") ? "&" : "?") + "error=" + errorMessage;
        }
        
        response.sendRedirect(redirectUrl);
    }
}

