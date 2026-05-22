package com.neogulmap.neogul_map.config.security;

import com.neogulmap.neogul_map.config.security.oauth.NativeOAuthCodeStore;
import com.neogulmap.neogul_map.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NativeOAuthCodeStoreTest {

    @Test
    @DisplayName("네이티브 OAuth code는 한 번만 토큰으로 교환된다")
    void codeCanBeConsumedOnlyOnce() {
        NativeOAuthCodeStore store = new NativeOAuthCodeStore();
        User user = User.builder()
                .id(7L)
                .email("native@nugulmap.com")
                .nickname("너굴")
                .build();

        String codeVerifier = "native-code-verifier-1234567890";
        String code = store.issue("access-token", "refresh-token", user, s256(codeVerifier), "S256");

        NativeOAuthCodeStore.Entry first = store.consume(code, codeVerifier).orElseThrow();

        assertThat(first.getAccessToken()).isEqualTo("access-token");
        assertThat(first.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(first.getUserId()).isEqualTo(7L);
        assertThat(first.isProfileComplete()).isTrue();
        assertThat(store.consume(code, codeVerifier)).isEmpty();
    }

    @Test
    @DisplayName("OAuth code는 잘못된 code_verifier로 교환되지 않는다")
    void codeRequiresMatchingVerifier() {
        NativeOAuthCodeStore store = new NativeOAuthCodeStore();
        User user = User.builder()
                .id(7L)
                .email("native@nugulmap.com")
                .nickname("너굴")
                .build();

        String code = store.issue("access-token", "refresh-token", user, s256("correct-verifier"), "S256");

        assertThat(store.consume(code, "wrong-verifier")).isEmpty();
        assertThat(store.consume(code, "correct-verifier")).isEmpty();
    }

    @Test
    @DisplayName("빈 OAuth code는 교환되지 않는다")
    void blankCodeIsRejected() {
        NativeOAuthCodeStore store = new NativeOAuthCodeStore();

        assertThat(store.consume(" ", "verifier")).isEmpty();
        assertThat(store.consume(null, "verifier")).isEmpty();
        assertThat(store.consume("code", null)).isEmpty();
    }

    private String s256(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
