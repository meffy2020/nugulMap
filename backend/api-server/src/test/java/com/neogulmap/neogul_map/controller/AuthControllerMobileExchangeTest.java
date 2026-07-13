package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.security.oauth.NativeOAuthCodeStore;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.dto.ApiResponse;
import com.neogulmap.neogul_map.dto.ErrorResponse;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.auth.AppleMobileLoginRequest;
import com.neogulmap.neogul_map.dto.auth.AuthTokenResponse;
import com.neogulmap.neogul_map.dto.auth.MobileOAuthExchangeRequest;
import com.neogulmap.neogul_map.service.AppleIdentityTokenService;
import com.neogulmap.neogul_map.service.AppleTokenEndpointService;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerMobileExchangeTest {

    private final TokenProvider tokenProvider = mock(TokenProvider.class);
    private final UserService userService = mock(UserService.class);
    private final ImageService imageService = mock(ImageService.class);
    private final NativeOAuthCodeStore nativeOAuthCodeStore = mock(NativeOAuthCodeStore.class);
    private final AppleIdentityTokenService appleIdentityTokenService = mock(AppleIdentityTokenService.class);
    private final AppleTokenEndpointService appleTokenEndpointService = mock(AppleTokenEndpointService.class);
    private final AuthController controller = new AuthController(
            tokenProvider,
            userService,
            imageService,
            nativeOAuthCodeStore,
            appleIdentityTokenService,
            appleTokenEndpointService
    );

    @Test
    @DisplayName("웹 로그아웃은 브라우저에서 읽을 수 없는 인증 쿠키를 서버가 직접 만료한다")
    void logoutExpiresHttpOnlyAuthenticationCookies() {
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Lax");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ApiResponse<Void>> result = controller.logout(response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(value -> assertThat(value)
                        .contains("accessToken=", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax"))
                .anySatisfy(value -> assertThat(value)
                        .contains("refreshToken=", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax"));
    }

    @Test
    @DisplayName("모바일 OAuth code 교환 성공 응답은 ApiResponse.data에 토큰을 담는다")
    void exchangeMobileOAuthCodeReturnsStandardSuccessEnvelope() {
        MobileOAuthExchangeRequest request = new MobileOAuthExchangeRequest();
        request.setCode("code-123");
        request.setCodeVerifier("verifier-123");
        when(nativeOAuthCodeStore.consume("code-123", "verifier-123")).thenReturn(Optional.of(
                NativeOAuthCodeStore.Entry.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .userId(1L)
                        .email("native@nugulmap.com")
                        .nickname("너굴")
                        .profileComplete(true)
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build()
        ));

        ResponseEntity<?> response = controller.exchangeMobileOAuthCode(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> envelope = (ApiResponse<?>) response.getBody();
        assertThat(envelope.isSuccess()).isTrue();
        assertThat(envelope.getData()).isInstanceOf(AuthTokenResponse.class);
        AuthTokenResponse data = (AuthTokenResponse) envelope.getData();
        assertThat(data.getAccessToken()).isEqualTo("access-token");
        assertThat(data.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("모바일 OAuth code 교환 실패 응답은 ErrorResponse 계약을 따른다")
    void exchangeMobileOAuthCodeReturnsStandardErrorEnvelope() {
        MobileOAuthExchangeRequest request = new MobileOAuthExchangeRequest();
        request.setCode("expired-code");
        request.setCodeVerifier("verifier-123");
        when(nativeOAuthCodeStore.consume("expired-code", "verifier-123")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.exchangeMobileOAuthCode(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.isSuccess()).isFalse();
        assertThat(error.getCode()).isEqualTo("V003");
        assertThat(error.getMessage()).contains("OAuth code");
    }

    @Test
    @DisplayName("인증 실패 응답은 내부 예외 메시지를 노출하지 않는다")
    void authenticationErrorsDoNotExposeInternalExceptionMessages() {
        when(tokenProvider.validRefreshToken("refresh-token"))
                .thenThrow(new IllegalStateException("jdbc:mysql://user:secret@internal-host"));
        when(tokenProvider.validAccessToken("access-token"))
                .thenThrow(new IllegalStateException("JWT_SECRET=do-not-expose"));

        ResponseEntity<?> refreshResponse = controller.refreshToken(Map.of("refreshToken", "refresh-token"));
        ResponseEntity<?> validateResponse = controller.validateToken(Map.of("token", "access-token"));

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(refreshResponse.getBody().toString()).doesNotContain("secret", "internal-host");
        assertThat(validateResponse.getBody().toString()).doesNotContain("JWT_SECRET", "do-not-expose");
    }

    @Test
    @DisplayName("회원가입 닉네임 정책 위반은 내부 오류로 숨기지 않는다")
    void completeSignupPropagatesNicknamePolicyViolation() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .oauthId("provider-id")
                .oauthProvider("google")
                .nickname(null)
                .build();
        doThrow(new ValidationException(ErrorCode.NICKNAME_CONTENT_REJECTED))
                .when(userService)
                .updateUser(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> controller.completeSignup(user, "F.U C-K", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Apple 로그인은 authorization code를 서버에서 검증하고 refresh token을 저장한다")
    void appleLoginExchangesAuthorizationCodeAndStoresRefreshToken() {
        AppleMobileLoginRequest request = new AppleMobileLoginRequest();
        request.setIdentityToken("identity-original");
        request.setAuthorizationCode("authorization-code");
        request.setFullName("너굴");
        AppleIdentityTokenService.AppleIdentity originalIdentity = mock(AppleIdentityTokenService.AppleIdentity.class);
        AppleIdentityTokenService.AppleIdentity exchangedIdentity = mock(AppleIdentityTokenService.AppleIdentity.class);
        when(originalIdentity.getSubject()).thenReturn("apple-subject");
        when(originalIdentity.getEmail()).thenReturn("apple@nugulmap.com");
        when(exchangedIdentity.getSubject()).thenReturn("apple-subject");
        when(appleIdentityTokenService.verify("identity-original")).thenReturn(originalIdentity);
        when(appleIdentityTokenService.verify("identity-from-code")).thenReturn(exchangedIdentity);
        when(appleTokenEndpointService.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new AppleTokenEndpointService.TokenGrant("apple-refresh-token", "identity-from-code"));
        User user = User.builder()
                .id(7L)
                .email("apple@nugulmap.com")
                .nickname("너굴")
                .oauthProvider("apple")
                .oauthId("apple-subject")
                .build();
        when(userService.processAppleUser(
                "apple-subject",
                "apple@nugulmap.com",
                "너굴",
                "apple-refresh-token"
        )).thenReturn(user);
        when(tokenProvider.generateAccessToken(user, Duration.ofHours(2))).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(user, Duration.ofDays(30))).thenReturn("refresh-token");

        ResponseEntity<?> response = controller.exchangeAppleIdentityToken(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).processAppleUser(
                "apple-subject",
                "apple@nugulmap.com",
                "너굴",
                "apple-refresh-token"
        );
    }
}
