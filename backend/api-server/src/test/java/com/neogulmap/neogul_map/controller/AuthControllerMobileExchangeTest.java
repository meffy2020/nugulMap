package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.security.oauth.NativeOAuthCodeStore;
import com.neogulmap.neogul_map.dto.ApiResponse;
import com.neogulmap.neogul_map.dto.ErrorResponse;
import com.neogulmap.neogul_map.dto.auth.AuthTokenResponse;
import com.neogulmap.neogul_map.dto.auth.MobileOAuthExchangeRequest;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerMobileExchangeTest {

    private final TokenProvider tokenProvider = mock(TokenProvider.class);
    private final UserService userService = mock(UserService.class);
    private final ImageService imageService = mock(ImageService.class);
    private final NativeOAuthCodeStore nativeOAuthCodeStore = mock(NativeOAuthCodeStore.class);
    private final AuthController controller = new AuthController(
            tokenProvider,
            userService,
            imageService,
            nativeOAuthCodeStore
    );

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
}
