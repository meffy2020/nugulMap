package com.neogulmap.neogul_map.config.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class OAuth2RedirectUrlResolver {

    @Value("${app.oauth2.allowed-redirect-uris:http://localhost,http://localhost:3000,https://nugulmap.com,https://www.nugulmap.com,nugulmap://oauth/callback}")
    private List<String> allowedRedirectUris;

    @Value("${app.oauth2.allowed-redirect-uri-prefixes:}")
    private List<String> allowedRedirectUriPrefixes;

    public Optional<String> resolveRedirectUri(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, OAuth2AuthorizationRequestBasedOnCookieRepository.REDIRECT_URI_PARAM_COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Optional.empty();
        }

        String decoded;
        try {
            decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("redirect_uri cookie decode 실패: {}", ex.getMessage());
            return Optional.empty();
        }

        String normalized = normalize(decoded);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        if (!isAuthorizedRedirectUri(normalized)) {
            log.warn("허용되지 않은 redirect_uri 요청 차단: {}", normalized);
            return Optional.empty();
        }

        return Optional.of(normalized);
    }

    private boolean isAuthorizedRedirectUri(String redirectUri) {
        String normalized = normalize(redirectUri);
        if (normalized.isEmpty()) {
            return false;
        }

        boolean exactMatch = allowedRedirectUris.stream()
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .anyMatch(allowed -> allowed.equalsIgnoreCase(normalized));
        if (exactMatch) {
            return true;
        }

        return allowedRedirectUriPrefixes.stream()
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .anyMatch(prefix -> normalized.toLowerCase().startsWith(prefix.toLowerCase()));
    }

    private String normalize(String uri) {
        if (uri == null) {
            return "";
        }

        String trimmed = uri.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
