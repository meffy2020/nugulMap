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
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;

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

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        OAuth2UserCustomService.CustomOAuth2User customOAuth2User = (OAuth2UserCustomService.CustomOAuth2User) oAuth2User;

        try {
            // 1. 사용자 처리 및 토큰 생성
            User user = userService.processOAuth2User(customOAuth2User);
            String accessToken = tokenProvider.generateToken(user, Duration.ofHours(2));
            String refreshToken = tokenProvider.generateToken(user, Duration.ofDays(30));

            // 2. 쿠키 설정
            addHttpOnlyCookie(response, ACCESS_TOKEN_COOKIE_NAME, accessToken, 7200);
            addHttpOnlyCookie(response, REFRESH_TOKEN_COOKIE_NAME, refreshToken, 2592000);

            // 3. 리다이렉트 경로 결정
            String targetUrl = determineTargetUrl(request, user, accessToken, refreshToken);

            log.info("OAuth2 로그인 성공: {}, 리다이렉트 경로: {}", user.getEmail(), targetUrl);
            authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

            // 4. 리다이렉트 실행
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            log.error("OAuth2 로그인 처리 중 에러 발생", e);
            getRedirectStrategy().sendRedirect(request, response, "/test/oauth2/failure?error=server_error");
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
        // fromHttpUrl 대신 fromUriString 사용
        return UriComponentsBuilder.fromUriString(baseUrl + "/signup")
                .queryParam("email", user.getEmail())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    return isBackendTest ? baseUrl + "/test" : baseUrl;
}

    private String buildMobileRedirectUrl(HttpServletRequest request, String redirectUri, String accessToken, String refreshToken, User user) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri);

        if (usesCodeResponseType(request)) {
            String codeChallenge = getDecodedCookieValue(request, OAuth2AuthorizationRequestBasedOnCookieRepository.CODE_CHALLENGE_PARAM_COOKIE_NAME);
            if (codeChallenge == null || codeChallenge.isBlank()) {
                return builder
                        .queryParam("error", "missing_code_challenge")
                        .build()
                        .encode(StandardCharsets.UTF_8)
                        .toUriString();
            }

            String codeChallengeMethod = getDecodedCookieValue(request, OAuth2AuthorizationRequestBasedOnCookieRepository.CODE_CHALLENGE_METHOD_PARAM_COOKIE_NAME);
            String code = nativeOAuthCodeStore.issue(accessToken, refreshToken, user, codeChallenge, codeChallengeMethod);
            builder.queryParam("code", code);
        } else {
            // 기존 Expo/iOS 클라이언트 호환 경로. 신규 네이티브 앱은 response_type=code를 사용한다.
            builder.queryParam("accessToken", accessToken);
        }

        builder.queryParam("profileComplete", user.isProfileComplete());

        if (!user.isProfileComplete()) {
            builder.queryParam("email", user.getEmail());
        }

        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private boolean usesCodeResponseType(HttpServletRequest request) {
        return "code".equalsIgnoreCase(getDecodedCookieValue(request, OAuth2AuthorizationRequestBasedOnCookieRepository.RESPONSE_TYPE_PARAM_COOKIE_NAME));
    }

    private String getDecodedCookieValue(HttpServletRequest request, String name) {
        var cookie = WebUtils.getCookie(request, name);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }

        try {
            return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private void addHttpOnlyCookie(HttpServletResponse response, String name, String value, int maxAge) {
        String cookieHeader = String.format("%s=%s; Path=/; HttpOnly; %sMax-Age=%d; SameSite=%s",
                name, value, cookieSecure ? "Secure; " : "", maxAge, cookieSameSite);
        response.addHeader("Set-Cookie", cookieHeader);
    }
}
