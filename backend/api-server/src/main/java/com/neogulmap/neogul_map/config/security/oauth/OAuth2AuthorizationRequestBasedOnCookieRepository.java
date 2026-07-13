package com.neogulmap.neogul_map.config.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Keeps the OAuth authorization request and native PKCE metadata on the server-side HTTP session.
 *
 * The legacy implementation serialized an OAuth2AuthorizationRequest into a client-controlled
 * cookie. That made OAuth state mutable and exposed a Java native-deserialization surface. Legacy
 * cookies are now only expired; their contents are never decoded.
 */
@Component
public class OAuth2AuthorizationRequestBasedOnCookieRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    public static final String RESPONSE_TYPE_PARAM_COOKIE_NAME = "response_type";
    public static final String CODE_CHALLENGE_PARAM_COOKIE_NAME = "code_challenge";
    public static final String CODE_CHALLENGE_METHOD_PARAM_COOKIE_NAME = "code_challenge_method";
    public static final String CLIENT_STATE_PARAM_NAME = "client_state";

    private static final String ATTRIBUTE_PREFIX =
            OAuth2AuthorizationRequestBasedOnCookieRepository.class.getName() + ".";
    private static final String REDIRECT_URI_ATTRIBUTE = ATTRIBUTE_PREFIX + REDIRECT_URI_PARAM_COOKIE_NAME;
    private static final String RESPONSE_TYPE_ATTRIBUTE = ATTRIBUTE_PREFIX + RESPONSE_TYPE_PARAM_COOKIE_NAME;
    private static final String CODE_CHALLENGE_ATTRIBUTE = ATTRIBUTE_PREFIX + CODE_CHALLENGE_PARAM_COOKIE_NAME;
    private static final String CODE_CHALLENGE_METHOD_ATTRIBUTE =
            ATTRIBUTE_PREFIX + CODE_CHALLENGE_METHOD_PARAM_COOKIE_NAME;
    private static final String CLIENT_STATE_ATTRIBUTE = ATTRIBUTE_PREFIX + CLIENT_STATE_PARAM_NAME;
    private static final Pattern PKCE_CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");
    private static final Pattern CLIENT_STATE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43,128}$");
    private static final int MAX_REDIRECT_URI_LENGTH = 2_048;

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
        if (authorizationRequest == null) {
            clearMetadata(request);
            expireLegacyCookies(response);
            return;
        }

        HttpSession session = request.getSession(true);
        setOrRemove(session, REDIRECT_URI_ATTRIBUTE, normalizedRedirectUri(request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME)));
        setOrRemove(session, RESPONSE_TYPE_ATTRIBUTE, normalizedResponseType(request.getParameter(RESPONSE_TYPE_PARAM_COOKIE_NAME)));
        setOrRemove(session, CODE_CHALLENGE_ATTRIBUTE, normalizedCodeChallenge(request.getParameter(CODE_CHALLENGE_PARAM_COOKIE_NAME)));
        setOrRemove(
                session,
                CODE_CHALLENGE_METHOD_ATTRIBUTE,
                normalizedCodeChallengeMethod(request.getParameter(CODE_CHALLENGE_METHOD_PARAM_COOKIE_NAME))
        );
        setOrRemove(session, CLIENT_STATE_ATTRIBUTE, normalizedClientState(request.getParameter(CLIENT_STATE_PARAM_NAME)));
        expireLegacyCookies(response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        transferMetadataToRequest(request);
        OAuth2AuthorizationRequest authorizationRequest = delegate.removeAuthorizationRequest(request, response);
        clearMetadata(request);
        expireLegacyCookies(response);
        return authorizationRequest;
    }

    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        delegate.removeAuthorizationRequest(request, response);
        clearMetadata(request);
        clearRequestMetadata(request);
        expireLegacyCookies(response);
    }

    public Optional<String> getRedirectUri(HttpServletRequest request) {
        return getSessionValue(request, REDIRECT_URI_ATTRIBUTE);
    }

    public Optional<String> getResponseType(HttpServletRequest request) {
        return getSessionValue(request, RESPONSE_TYPE_ATTRIBUTE);
    }

    public Optional<String> getCodeChallenge(HttpServletRequest request) {
        return getSessionValue(request, CODE_CHALLENGE_ATTRIBUTE);
    }

    public Optional<String> getCodeChallengeMethod(HttpServletRequest request) {
        return getSessionValue(request, CODE_CHALLENGE_METHOD_ATTRIBUTE);
    }

    public Optional<String> getClientState(HttpServletRequest request) {
        return getSessionValue(request, CLIENT_STATE_ATTRIBUTE);
    }

    private Optional<String> getSessionValue(HttpServletRequest request, String attributeName) {
        Object requestValue = request.getAttribute(attributeName);
        if (requestValue instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(attributeName);
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    private void transferMetadataToRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        transferMetadataValue(request, session, REDIRECT_URI_ATTRIBUTE);
        transferMetadataValue(request, session, RESPONSE_TYPE_ATTRIBUTE);
        transferMetadataValue(request, session, CODE_CHALLENGE_ATTRIBUTE);
        transferMetadataValue(request, session, CODE_CHALLENGE_METHOD_ATTRIBUTE);
        transferMetadataValue(request, session, CLIENT_STATE_ATTRIBUTE);
    }

    private void transferMetadataValue(HttpServletRequest request, HttpSession session, String attributeName) {
        Object value = session.getAttribute(attributeName);
        if (value instanceof String text && !text.isBlank()) {
            request.setAttribute(attributeName, text);
        }
    }

    private void clearRequestMetadata(HttpServletRequest request) {
        request.removeAttribute(REDIRECT_URI_ATTRIBUTE);
        request.removeAttribute(RESPONSE_TYPE_ATTRIBUTE);
        request.removeAttribute(CODE_CHALLENGE_ATTRIBUTE);
        request.removeAttribute(CODE_CHALLENGE_METHOD_ATTRIBUTE);
        request.removeAttribute(CLIENT_STATE_ATTRIBUTE);
    }

    private void setOrRemove(HttpSession session, String attributeName, String value) {
        if (value == null) {
            session.removeAttribute(attributeName);
        } else {
            session.setAttribute(attributeName, value);
        }
    }

    private void clearMetadata(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute(REDIRECT_URI_ATTRIBUTE);
        session.removeAttribute(RESPONSE_TYPE_ATTRIBUTE);
        session.removeAttribute(CODE_CHALLENGE_ATTRIBUTE);
        session.removeAttribute(CODE_CHALLENGE_METHOD_ATTRIBUTE);
        session.removeAttribute(CLIENT_STATE_ATTRIBUTE);
    }

    private String normalizedRedirectUri(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > MAX_REDIRECT_URI_LENGTH ? null : normalized;
    }

    private String normalizedResponseType(String value) {
        return value != null && "code".equalsIgnoreCase(value.trim()) ? "code" : null;
    }

    private String normalizedCodeChallenge(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return PKCE_CHALLENGE_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String normalizedCodeChallengeMethod(String value) {
        return value != null && "S256".equals(value.trim().toUpperCase(Locale.ROOT)) ? "S256" : null;
    }

    private String normalizedClientState(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return CLIENT_STATE_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private void expireLegacyCookies(HttpServletResponse response) {
        expireLegacyCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        expireLegacyCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME);
        expireLegacyCookie(response, RESPONSE_TYPE_PARAM_COOKIE_NAME);
        expireLegacyCookie(response, CODE_CHALLENGE_PARAM_COOKIE_NAME);
        expireLegacyCookie(response, CODE_CHALLENGE_METHOD_PARAM_COOKIE_NAME);
    }

    private void expireLegacyCookie(HttpServletResponse response, String name) {
        String header = String.format(
                "%s=; Path=/; HttpOnly; %sMax-Age=0; SameSite=%s",
                name,
                cookieSecure ? "Secure; " : "",
                cookieSameSite
        );
        response.addHeader("Set-Cookie", header);
    }
}
