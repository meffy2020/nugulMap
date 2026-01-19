package com.neogulmap.neogul_map.config.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import org.springframework.web.util.WebUtils;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

import java.util.Base64;

@Slf4j
@Component
public class OAuth2AuthorizationRequestBasedOnCookieRepository 
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    
    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    private static final int COOKIE_EXPIRE_SECONDS = 180;
    
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;
    
    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }
    
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, 
                                       HttpServletRequest request, 
                                       HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(request, response);
            return;
        }
        
        // 1. 인가 요청 정보 저장
        addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, 
                 serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
        
        // 2. 로그인 후 돌아갈 리다이렉트 URI 저장 (프론트엔드 주소 등)
        String redirectUriAfterLogin = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        if (redirectUriAfterLogin != null && !redirectUriAfterLogin.isEmpty()) {
            addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriAfterLogin, COOKIE_EXPIRE_SECONDS);
        }
    }
    
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, 
                                                               HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        removeAuthorizationRequestCookies(request, response);
        return authorizationRequest;
    }

    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
    }
    
    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        // SameSite 설정을 위해 SuccessHandler와 동일하게 헤더 방식으로 통일
        String cookieHeader = String.format("%s=%s; Path=/; HttpOnly; %sMax-Age=%d; SameSite=%s", 
                name, value, cookieSecure ? "Secure; " : "", maxAge, cookieSameSite);
        response.addHeader("Set-Cookie", cookieHeader);
    }
    
    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        // 쿠키를 삭제할 때도 헤더 방식으로 확실하게 처리
        String cookieHeader = String.format("%s=; Path=/; HttpOnly; %sMax-Age=0; SameSite=%s", 
                name, cookieSecure ? "Secure; " : "", cookieSameSite);
        response.addHeader("Set-Cookie", cookieHeader);
    }

    private java.util.Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        // WebUtils를 사용하면 지저분한 for문 없이 한 줄로 쿠키를 찾을 수 있습니다.
        return java.util.Optional.ofNullable(WebUtils.getCookie(request, name));
    }
    
    private String serialize(Object object) {
        // Spring의 SerializationUtils는 기본적으로 바이트 배열을 반환합니다.
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(object));
    }
    
    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        // 역직렬화 시 바이트 배열로 변환 후 캐스팅
        byte[] decoded = Base64.getUrlDecoder().decode(cookie.getValue());
        try (ByteArrayInputStream bis = new ByteArrayInputStream(decoded);
         ObjectInputStream ois = new ObjectInputStream(bis)) {
        return (OAuth2AuthorizationRequest) ois.readObject();
    } catch (Exception e) {
        log.error("OAuth2 요청 역직렬화 중 에러 발생: {}", e.getMessage());
        return null;
    } }
}