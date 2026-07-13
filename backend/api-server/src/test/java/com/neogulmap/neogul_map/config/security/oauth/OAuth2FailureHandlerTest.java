package com.neogulmap.neogul_map.config.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2FailureHandlerTest {

    @Test
    void jsonFailureDoesNotReflectProviderExceptionDetails() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler(
                mock(OAuth2AuthorizationRequestBasedOnCookieRepository.class),
                mock(OAuth2RedirectUrlResolver.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("provider-secret-or-pii")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("oauth2_authentication_failed")
                .doesNotContain("provider-secret-or-pii");
    }

    @Test
    void nativeFailureCallbackEchoesClientStateAndClearsAuthorizationMetadata() throws Exception {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository =
                mock(OAuth2AuthorizationRequestBasedOnCookieRepository.class);
        OAuth2RedirectUrlResolver redirectUrlResolver = mock(OAuth2RedirectUrlResolver.class);
        OAuth2FailureHandler handler = new OAuth2FailureHandler(repository, redirectUrlResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String clientState = "s".repeat(43);
        when(redirectUrlResolver.resolveRedirectUri(request))
                .thenReturn(Optional.of("nugulmap://oauth/callback"));
        when(repository.getClientState(request)).thenReturn(Optional.of(clientState));

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("provider-secret-or-pii")
        );

        assertThat(response.getRedirectedUrl())
                .contains("error=oauth2_authentication_failed")
                .contains("client_state=" + clientState)
                .doesNotContain("provider-secret-or-pii");
        verify(repository).removeAuthorizationRequest(request, response);
    }
}
