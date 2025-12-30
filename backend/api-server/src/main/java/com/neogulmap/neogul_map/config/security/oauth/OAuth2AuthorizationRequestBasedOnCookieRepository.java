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
import org.springframework.web.util.CookieGenerator;

import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
public class OAuth2AuthorizationRequestBasedOnCookieRepository 
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    
    private static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    private static final int COOKIE_EXPIRE_SECONDS = 180;
    
    private final CookieGenerator cookieGenerator = new CookieGenerator();
    
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;
    
    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;
    
    public OAuth2AuthorizationRequestBasedOnCookieRepository() {
        cookieGenerator.setCookieName(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        cookieGenerator.setCookieMaxAge(COOKIE_EXPIRE_SECONDS);
    }
    
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
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
            return;
        }
        
        addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, 
                 serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
        
        String redirectUriAfterLogin = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        if (redirectUriAfterLogin != null && !redirectUriAfterLogin.isEmpty()) {
            addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriAfterLogin, COOKIE_EXPIRE_SECONDS);
        }
    }
    
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, 
                                                               HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        
        // 쿠키 삭제
        if (authorizationRequest != null) {
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
        }
        
        return authorizationRequest;
    }
    
    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(cookieSecure);
        
        // SameSite 설정 (SameSite=None은 secure가 true일 때만 가능)
        if ("None".equals(cookieSameSite)) {
            if (cookieSecure) {
                // SameSite=None은 Secure가 필수이므로 Set-Cookie 헤더로 직접 설정
                response.setHeader("Set-Cookie", 
                    String.format("%s=%s; Path=/; HttpOnly; Secure; Max-Age=%d; SameSite=None", 
                        name, value, maxAge));
                return;
            } else {
                log.warn("SameSite=None은 Secure 쿠키에서만 사용 가능합니다. Lax로 변경합니다.");
                cookieSameSite = "Lax";
            }
        }
        
        // SameSite 속성은 Java Cookie API에서 직접 지원하지 않으므로
        // Servlet 6.0+ 또는 Spring Boot 3.1+에서는 자동으로 처리되지만,
        // 명시적으로 설정하려면 ResponseCookie를 사용하거나 헤더를 직접 설정해야 합니다.
        // 여기서는 기본 Cookie를 사용하고, 필요시 application.yml에서 설정합니다.
        response.addCookie(cookie);
    }
    
    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                }
            }
        }
    }
    
    private Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return Optional.of(cookie);
                }
            }
        }
        return Optional.empty();
    }
    
    private String serialize(Object object) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(object));
    }
    
    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                Base64.getUrlDecoder().decode(cookie.getValue()));
    }
}