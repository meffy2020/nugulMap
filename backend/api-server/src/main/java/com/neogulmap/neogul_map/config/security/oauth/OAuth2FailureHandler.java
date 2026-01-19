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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {
    
    private final OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;
    
    @Value("${app.frontend-url}")
    private String frontendUrl; // 예: http://localhost

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, 
                                        HttpServletResponse response, 
                                        AuthenticationException exception) throws IOException, ServletException {
        
        log.error("OAuth2 인증 실패: {}", exception.getMessage());
        
        // 1. 쿠키 정리
        authorizationRequestRepository.removeAuthorizationRequest(request, response);
        
        // 2. JSON 요청 처리 (Postman이나 앱 테스트용)
        if (isJsonRequest(request)) {
            sendJsonResponse(response, exception);
            return;
        }
        
        // 3. 리다이렉트 URL 결정
        String targetUrl = determineTargetUrl(request, exception);
        
        log.info("OAuth2 실패 리다이렉트 실행: {}", targetUrl);
        
        // 4. 리다이렉트 실행
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String determineTargetUrl(HttpServletRequest request, AuthenticationException exception) {
        String requestUrl = request.getRequestURL().toString();
        
        // 백엔드 직접 접근(8080)인지 확인 (테스트용)
        boolean isBackendTest = requestUrl != null && requestUrl.contains(":8080");
        
        // 기준 주소 설정 (성공 핸들러와 동일 로직)
        String baseUrl = isBackendTest ? "http://localhost:8080/api" : frontendUrl;

        // 에러 페이지 경로 설정
        // 성공 핸들러에서 썼던 UriComponentsBuilder.fromUriString 사용 (deprecated 회피)
        return UriComponentsBuilder.fromUriString(baseUrl + "/test/oauth2/failure")
                .queryParam("error", exception.getLocalizedMessage())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        return acceptHeader != null && acceptHeader.contains("application/json");
    }

    private void sendJsonResponse(HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
            "{\"success\":false,\"message\":\"OAuth2 인증 실패\",\"error\":\"%s\"}",
            exception.getMessage()
        ));
    }
}