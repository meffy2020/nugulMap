package com.neogulmap.neogul_map.config.security.jwt;

import com.neogulmap.neogul_map.domain.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenProviderTokenTypeTest {

    private final TokenProvider tokenProvider = new TokenProvider(
            Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
            ),
            7_200
    );
    private final User user = User.builder().id(7L).email("user@example.com").build();

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String accessToken = tokenProvider.generateAccessToken(user, Duration.ofHours(2));

        assertThat(tokenProvider.validAccessToken(accessToken)).isTrue();
        assertThat(tokenProvider.validRefreshToken(accessToken)).isFalse();
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequests() {
        String refreshToken = tokenProvider.generateRefreshToken(user, Duration.ofDays(30));

        assertThat(tokenProvider.validRefreshToken(refreshToken)).isTrue();
        assertThat(tokenProvider.validAccessToken(refreshToken)).isFalse();
    }
}
