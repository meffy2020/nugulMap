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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenProvider tokenProvider;
    private final UserService userService;
    private final OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;
    private final OAuth2RedirectUrlResolver redirectUrlResolver;
    private final NativeOAuthCodeStore nativeOAuthCodeStore;

    @Value("${app.frontend-url}")
    private String frontendUrl; // 예: http://localhost

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.oauth.mobile-code-required:false}")
    private boolean mobileCodeRequired;

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String OAUTH2_PROCESSING_FAILED = "oauth2_processing_failed";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        OAuth2UserCustomService.CustomOAuth2User customOAuth2User = (OAuth2UserCustomService.CustomOAuth2User) oAuth2User;

        try {
            // 1. 사용자 처리 및 토큰 생성
            User user = userService.processOAuth2User(customOAuth2User);
            String accessToken = tokenProvider.generateAccessToken(user, Duration.ofHours(2));
            String refreshToken = tokenProvider.generateRefreshToken(user, Duration.ofDays(30));

            // 2. 쿠키 설정
            addHttpOnlyCookie(response, ACCESS_TOKEN_COOKIE_NAME, accessToken, 7200);
            addHttpOnlyCookie(response, REFRESH_TOKEN_COOKIE_NAME, refreshToken, 2592000);

            // 3. 리다이렉트 경로 결정
            String targetUrl = determineTargetUrl(request, user, accessToken, refreshToken);

            log.info("OAuth2 로그인 성공 - User ID: {}", user.getId());
            authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

            // 4. 리다이렉트 실행
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            log.error("OAuth2 로그인 처리 중 에러 발생", e);
            String failureTargetUrl = determineProcessingFailureTargetUrl(request);
            expireAuthenticationCookies(response);
            try {
                authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
            } catch (RuntimeException cleanupError) {
                log.warn("OAuth2 실패 metadata 정리 중 오류 발생: {}", cleanupError.getClass().getSimpleName());
            }
            getRedirectStrategy().sendRedirect(request, response, failureTargetUrl);
        }
    }

   private String determineTargetUrl(HttpServletRequest request, User user, String accessToken, String refreshToken) {
    String mobileRedirectUrl = redirectUrlResolver.resolveRedirectUri(request)
            .map(redirectUri -> buildMobileRedirectUrl(request, redirectUri, accessToken, refreshToken, user))
            .orElse(null);
    if (mobileRedirectUrl != null) {
        return mobileRedirectUrl;
    }

    String referer = request.getHeader("Referer");
    String requestUrl = request.getRequestURL().toString();
    
    boolean isBackendTest = (requestUrl != null && requestUrl.contains(":8080")) || 
                           (referer != null && referer.contains(":8080"));

    String baseUrl = isBackendTest ? "http://localhost:8080/api" : frontendUrl;

    if (!user.isProfileComplete()) {
        return UriComponentsBuilder.fromUriString(baseUrl + "/signup")
                .build()
                .toUriString();
    }

    return isBackendTest ? baseUrl + "/test" : baseUrl;
}

    private String buildMobileRedirectUrl(HttpServletRequest request, String redirectUri, String accessToken, String refreshToken, User user) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri);

        if (usesCodeResponseType(request)) {
            String codeChallenge = authorizationRequestRepository.getCodeChallenge(request).orElse(null);
            if (codeChallenge == null || codeChallenge.isBlank()) {
                return builder
                        .queryParam("error", "missing_code_challenge")
                        .build()
                        .encode(StandardCharsets.UTF_8)
                        .toUriString();
            }

            String codeChallengeMethod = authorizationRequestRepository.getCodeChallengeMethod(request).orElse(null);
            String code = nativeOAuthCodeStore.issue(accessToken, refreshToken, user, codeChallenge, codeChallengeMethod);
            builder.queryParam("code", code);
        } else if (mobileCodeRequired) {
            // 운영 네이티브 앱은 PKCE 일회성 code 교환만 허용한다.
            builder.queryParam("error", "code_response_required");
        } else {
            // 기존 Expo/iOS 클라이언트 호환 경로. 신규 네이티브 앱은 response_type=code를 사용한다.
            builder.queryParam("accessToken", accessToken);
        }

        builder.queryParam("profileComplete", user.isProfileComplete());

        authorizationRequestRepository.getClientState(request)
                .ifPresent(clientState -> builder.queryParam(
                        OAuth2AuthorizationRequestBasedOnCookieRepository.CLIENT_STATE_PARAM_NAME,
                        clientState
                ));

        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private boolean usesCodeResponseType(HttpServletRequest request) {
        return authorizationRequestRepository.getResponseType(request)
                .map("code"::equalsIgnoreCase)
                .orElse(false);
    }

    private String determineProcessingFailureTargetUrl(HttpServletRequest request) {
        try {
            return redirectUrlResolver.resolveRedirectUri(request)
                    .map(redirectUri -> buildMobileProcessingFailureUrl(request, redirectUri))
                    .orElse("/test/oauth2/failure?error=server_error");
        } catch (RuntimeException redirectError) {
            log.warn("OAuth2 실패 callback 구성 중 오류 발생: {}", redirectError.getClass().getSimpleName());
            return "/test/oauth2/failure?error=server_error";
        }
    }

    private String buildMobileProcessingFailureUrl(HttpServletRequest request, String redirectUri) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", OAUTH2_PROCESSING_FAILED);
        authorizationRequestRepository.getClientState(request)
                .ifPresent(clientState -> builder.queryParam(
                        OAuth2AuthorizationRequestBasedOnCookieRepository.CLIENT_STATE_PARAM_NAME,
                        clientState
                ));
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private void expireAuthenticationCookies(HttpServletResponse response) {
        addHttpOnlyCookie(response, ACCESS_TOKEN_COOKIE_NAME, "", 0);
        addHttpOnlyCookie(response, REFRESH_TOKEN_COOKIE_NAME, "", 0);
    }

    private void addHttpOnlyCookie(HttpServletResponse response, String name, String value, int maxAge) {
        String cookieHeader = String.format("%s=%s; Path=/; HttpOnly; %sMax-Age=%d; SameSite=%s",
                name, value, cookieSecure ? "Secure; " : "", maxAge, cookieSameSite);
        response.addHeader("Set-Cookie", cookieHeader);
    }
}
