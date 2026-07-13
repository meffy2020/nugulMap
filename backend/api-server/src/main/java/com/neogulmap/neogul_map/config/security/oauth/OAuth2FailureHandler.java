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
    private final OAuth2RedirectUrlResolver redirectUrlResolver;
    
    @Value("${app.frontend-url}")
    private String frontendUrl; // 예: http://localhost

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, 
                                        HttpServletResponse response, 
                                        AuthenticationException exception) throws IOException, ServletException {
        
        log.warn("OAuth2 인증 실패: {}", exception.getClass().getSimpleName());
        
        // 세션의 redirect metadata는 정리 전에 읽어야 한다.
        String targetUrl = isJsonRequest(request) ? null : determineTargetUrl(request);
        authorizationRequestRepository.removeAuthorizationRequest(request, response);

        // JSON 요청 처리 (Postman이나 앱 테스트용)
        if (isJsonRequest(request)) {
            sendJsonResponse(response);
            return;
        }
        
        log.info("OAuth2 실패 리다이렉트 실행");
        
        // 리다이렉트 실행
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String determineTargetUrl(HttpServletRequest request) {
        String mobileTarget = redirectUrlResolver.resolveRedirectUri(request)
                .map(redirectUri -> buildMobileFailureUrl(request, redirectUri))
                .orElse(null);
        if (mobileTarget != null) {
            return mobileTarget;
        }

        String requestUrl = request.getRequestURL().toString();
        
        // 백엔드 직접 접근(8080)인지 확인 (테스트용)
        boolean isBackendTest = requestUrl != null && requestUrl.contains(":8080");
        
        // 기준 주소 설정 (성공 핸들러와 동일 로직)
        String baseUrl = isBackendTest ? "http://localhost:8080/api" : frontendUrl;

        // 에러 페이지 경로 설정
        // 성공 핸들러에서 썼던 UriComponentsBuilder.fromUriString 사용 (deprecated 회피)
        return UriComponentsBuilder.fromUriString(baseUrl + "/test/oauth2/failure")
                .queryParam("error", "oauth2_authentication_failed")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private String buildMobileFailureUrl(HttpServletRequest request, String redirectUri) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", "oauth2_authentication_failed");
        authorizationRequestRepository.getClientState(request)
                .ifPresent(clientState -> builder.queryParam(
                        OAuth2AuthorizationRequestBasedOnCookieRepository.CLIENT_STATE_PARAM_NAME,
                        clientState
                ));
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        return acceptHeader != null && acceptHeader.contains("application/json");
    }

    private void sendJsonResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"OAuth2 인증 실패\","
                        + "\"error\":\"oauth2_authentication_failed\"}"
        );
    }
}
