package com.neogulmap.neogul_map.config.security.oauth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthorizationRequestSessionRepositoryTest {

    @Test
    void authorizationRequestAndPkceMetadataAreBoundToTheServerSession() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository = repository();
        MockHttpServletRequest start = new MockHttpServletRequest();
        String clientState = "c".repeat(43);
        start.setParameter("redirect_uri", "nugulmap://oauth/callback");
        start.setParameter("response_type", "code");
        start.setParameter("code_challenge", "A".repeat(43));
        start.setParameter("code_challenge_method", "S256");
        start.setParameter("client_state", clientState);
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client-id")
                .redirectUri("https://api.example/login/oauth2/code/provider")
                .state("unpredictable-state")
                .build();

        repository.saveAuthorizationRequest(authorizationRequest, start, response);

        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setSession(start.getSession(false));
        callback.setParameter("state", "unpredictable-state");
        assertThat(repository.loadAuthorizationRequest(callback).getState()).isEqualTo("unpredictable-state");
        assertThat(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse()).getState())
                .isEqualTo("unpredictable-state");
        assertThat(repository.getRedirectUri(callback)).contains("nugulmap://oauth/callback");
        assertThat(repository.getResponseType(callback)).contains("code");
        assertThat(repository.getCodeChallenge(callback)).contains("A".repeat(43));
        assertThat(repository.getCodeChallengeMethod(callback)).contains("S256");
        assertThat(repository.getClientState(callback)).contains(clientState);

        repository.removeAuthorizationRequestCookies(callback, new MockHttpServletResponse());
        assertThat(repository.getClientState(callback)).isEmpty();

        MockHttpServletRequest nextRequest = new MockHttpServletRequest();
        nextRequest.setSession(start.getSession(false));
        assertThat(repository.getRedirectUri(nextRequest)).isEmpty();
        assertThat(repository.getCodeChallenge(nextRequest)).isEmpty();
        assertThat(repository.getClientState(nextRequest)).isEmpty();
    }

    @Test
    void clientControlledLegacyCookieIsNeverDeserialized() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository = repository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(
                OAuth2AuthorizationRequestBasedOnCookieRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                "rO0ABXNyABNmb3JnZWQtbmF0aXZlLW9iamVjdA"
        ));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
        assertThat(repository.getRedirectUri(request)).isEmpty();
    }

    @Test
    void invalidPkceMetadataIsNotStored() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository = repository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("response_type", "token");
        request.setParameter("code_challenge", "too-short");
        request.setParameter("code_challenge_method", "plain");

        repository.saveAuthorizationRequest(
                OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("https://provider.example/authorize")
                        .clientId("client-id")
                        .state("state")
                        .build(),
                request,
                new MockHttpServletResponse()
        );

        assertThat(repository.getResponseType(request)).isEmpty();
        assertThat(repository.getCodeChallenge(request)).isEmpty();
        assertThat(repository.getCodeChallengeMethod(request)).isEmpty();
    }

    @Test
    void clientStateAcceptsOnlyBase64UrlWithoutPadding() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository = repository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("client_state", "invalid+state=" + "x".repeat(43));
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client-id")
                .state("provider-generated-state")
                .build();

        repository.saveAuthorizationRequest(authorizationRequest, request, new MockHttpServletResponse());

        assertThat(repository.getClientState(request)).isEmpty();

        request.setParameter("client_state", "A_b-" + "z".repeat(124));
        repository.saveAuthorizationRequest(authorizationRequest, request, new MockHttpServletResponse());

        assertThat(repository.getClientState(request)).contains("A_b-" + "z".repeat(124));
    }

    private OAuth2AuthorizationRequestBasedOnCookieRepository repository() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository =
                new OAuth2AuthorizationRequestBasedOnCookieRepository();
        ReflectionTestUtils.setField(repository, "cookieSecure", true);
        ReflectionTestUtils.setField(repository, "cookieSameSite", "Lax");
        return repository;
    }
}
