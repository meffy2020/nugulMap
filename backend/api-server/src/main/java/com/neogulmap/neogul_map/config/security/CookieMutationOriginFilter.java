package com.neogulmap.neogul_map.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Protects browser cookie-authenticated mutations without imposing a CSRF-token
 * contract on native clients that authenticate with an Authorization header.
 */
public final class CookieMutationOriginFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> AUTH_COOKIE_NAMES = Set.of("accessToken", "refreshToken");

    private final boolean enabled;
    private final Set<String> allowedOrigins;

    public CookieMutationOriginFilter(boolean enabled, String[] allowedOrigins) {
        this.enabled = enabled;
        this.allowedOrigins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins)
                .map(CookieMutationOriginFilter::normalizeOrigin)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || request == null
                || SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                || hasBearerAuthorization(request)
                || !hasAuthenticationCookie(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestOrigin = normalizeOrigin(request.getHeader("Origin"));
        if (!StringUtils.hasText(requestOrigin)) {
            requestOrigin = normalizeOrigin(request.getHeader("Referer"));
        }

        if (!StringUtils.hasText(requestOrigin) || !allowedOrigins.contains(requestOrigin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"CSRF_ORIGIN_DENIED\",\"message\":\"요청 출처를 확인할 수 없습니다.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean hasBearerAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && StringUtils.hasText(authorization.substring(7));
    }

    private static boolean hasAuthenticationCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        return Arrays.stream(cookies)
                .anyMatch(cookie -> AUTH_COOKIE_NAMES.contains(cookie.getName())
                        && StringUtils.hasText(cookie.getValue()));
    }

    static String normalizeOrigin(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            URI uri = new URI(rawValue.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }

            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || (scheme.equalsIgnoreCase("http") && port == 80)
                    || (scheme.equalsIgnoreCase("https") && port == 443);
            String normalizedHost = host.contains(":") ? "[" + host.toLowerCase(Locale.ROOT) + "]" : host.toLowerCase(Locale.ROOT);
            return scheme.toLowerCase(Locale.ROOT) + "://" + normalizedHost + (defaultPort ? "" : ":" + port);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }
}
