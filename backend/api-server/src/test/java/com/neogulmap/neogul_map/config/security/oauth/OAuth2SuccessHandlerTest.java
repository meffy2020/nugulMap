package com.neogulmap.neogul_map.config.security.oauth;

import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    @Test
    void productionPolicyNeverPlacesAccessTokenInLegacyMobileRedirect() {
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                mock(TokenProvider.class),
                mock(UserService.class),
                mock(OAuth2AuthorizationRequestBasedOnCookieRepository.class),
                mock(OAuth2RedirectUrlResolver.class),
                mock(NativeOAuthCodeStore.class)
        );
        ReflectionTestUtils.setField(handler, "mobileCodeRequired", true);

        String redirect = ReflectionTestUtils.invokeMethod(
                handler,
                "buildMobileRedirectUrl",
                new MockHttpServletRequest(),
                "nugulmap://oauth/callback",
                "sensitive-access-token",
                "sensitive-refresh-token",
                User.builder().id(1L).nickname("너굴이").build()
        );

        assertThat(redirect)
                .contains("error=code_response_required")
                .doesNotContain("sensitive-access-token")
                .doesNotContain("sensitive-refresh-token")
                .doesNotContain("accessToken=");
    }

    @Test
    void nativeCodeCallbackEchoesClientStateSeparatelyFromProviderState() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository =
                mock(OAuth2AuthorizationRequestBasedOnCookieRepository.class);
        NativeOAuthCodeStore codeStore = mock(NativeOAuthCodeStore.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                mock(TokenProvider.class),
                mock(UserService.class),
                repository,
                mock(OAuth2RedirectUrlResolver.class),
                codeStore
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "provider-generated-state");
        String clientState = "A_b-" + "z".repeat(124);
        when(repository.getResponseType(request)).thenReturn(Optional.of("code"));
        when(repository.getCodeChallenge(request)).thenReturn(Optional.of("C".repeat(43)));
        when(repository.getCodeChallengeMethod(request)).thenReturn(Optional.of("S256"));
        when(repository.getClientState(request)).thenReturn(Optional.of(clientState));
        when(codeStore.issue(anyString(), anyString(), any(User.class), anyString(), anyString()))
                .thenReturn("single-use-code");

        String redirect = ReflectionTestUtils.invokeMethod(
                handler,
                "buildMobileRedirectUrl",
                request,
                "nugulmap://oauth/callback",
                "sensitive-access-token",
                "sensitive-refresh-token",
                User.builder().id(1L).email("user@example.com").nickname("너굴이").build()
        );

        assertThat(redirect)
                .contains("code=single-use-code")
                .contains("client_state=" + clientState)
                .doesNotContain("state=provider-generated-state")
                .doesNotContain("accessToken=")
                .doesNotContain("refreshToken=")
                .doesNotContain("email=")
                .doesNotContain("user%40example.com");
    }

    @Test
    void webSignupRedirectDoesNotExposeEmailInTheUrl() {
        OAuth2AuthorizationRequestBasedOnCookieRepository repository =
                mock(OAuth2AuthorizationRequestBasedOnCookieRepository.class);
        OAuth2RedirectUrlResolver redirectUrlResolver = mock(OAuth2RedirectUrlResolver.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                mock(TokenProvider.class),
                mock(UserService.class),
                repository,
                redirectUrlResolver,
                mock(NativeOAuthCodeStore.class)
        );
        ReflectionTestUtils.setField(handler, "frontendUrl", "https://nugulmap.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("api.nugulmap.com");
        request.setRequestURI("/api/login/oauth2/code/google");
        when(redirectUrlResolver.resolveRedirectUri(request)).thenReturn(Optional.empty());

        String redirect = ReflectionTestUtils.invokeMethod(
                handler,
                "determineTargetUrl",
                request,
                User.builder().id(1L).email("user@example.com").nickname(null).build(),
                "access-token",
                "refresh-token"
        );

        assertThat(redirect)
                .isEqualTo("https://nugulmap.com/signup")
                .doesNotContain("email=")
                .doesNotContain("user%40example.com");
    }

    @Test
    void nativeProcessingFailureReturnsToVerifiedCallbackWithClientStateAndExpiresIssuedCookies() throws Exception {
        TokenProvider tokenProvider = mock(TokenProvider.class);
        UserService userService = mock(UserService.class);
        OAuth2AuthorizationRequestBasedOnCookieRepository repository =
                mock(OAuth2AuthorizationRequestBasedOnCookieRepository.class);
        OAuth2RedirectUrlResolver redirectUrlResolver = mock(OAuth2RedirectUrlResolver.class);
        NativeOAuthCodeStore codeStore = mock(NativeOAuthCodeStore.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                tokenProvider,
                userService,
                repository,
                redirectUrlResolver,
                codeStore
        );
        ReflectionTestUtils.setField(handler, "mobileCodeRequired", true);
        ReflectionTestUtils.setField(handler, "cookieSecure", true);
        ReflectionTestUtils.setField(handler, "cookieSameSite", "Lax");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "provider-generated-state");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = mock(Authentication.class);
        OAuth2UserCustomService.CustomOAuth2User principal =
                mock(OAuth2UserCustomService.CustomOAuth2User.class);
        User user = User.builder().id(1L).email("user@example.com").nickname("너굴이").build();
        String clientState = "A_b-" + "z".repeat(124);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userService.processOAuth2User(principal)).thenReturn(user);
        when(tokenProvider.generateAccessToken(any(User.class), any())).thenReturn("sensitive-access-token");
        when(tokenProvider.generateRefreshToken(any(User.class), any())).thenReturn("sensitive-refresh-token");
        when(redirectUrlResolver.resolveRedirectUri(request)).thenReturn(Optional.of("nugulmap://oauth/callback"));
        when(repository.getResponseType(request)).thenReturn(Optional.of("code"));
        when(repository.getCodeChallenge(request)).thenReturn(Optional.of("C".repeat(43)));
        when(repository.getCodeChallengeMethod(request)).thenReturn(Optional.of("S256"));
        when(repository.getClientState(request)).thenReturn(Optional.of(clientState));
        when(codeStore.issue(anyString(), anyString(), any(User.class), anyString(), anyString()))
                .thenThrow(new IllegalStateException("sensitive-provider-detail"));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .contains("nugulmap://oauth/callback")
                .contains("error=oauth2_processing_failed")
                .contains("client_state=" + clientState)
                .doesNotContain("state=provider-generated-state")
                .doesNotContain("sensitive-provider-detail")
                .doesNotContain("sensitive-access-token")
                .doesNotContain("sensitive-refresh-token");
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies)
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("accessToken=;")
                        .contains("Max-Age=0", "Secure", "HttpOnly", "SameSite=Lax"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("refreshToken=;")
                        .contains("Max-Age=0", "Secure", "HttpOnly", "SameSite=Lax"));
        verify(repository).removeAuthorizationRequestCookies(request, response);
    }
}
