package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleRefreshTokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsRefreshTokenWithoutPersistingPlaintext() {
        AppleRefreshTokenCipher cipher = new AppleRefreshTokenCipher(KEY, new SecureRandom());

        String encrypted = cipher.encrypt("apple-refresh-token");

        assertThat(encrypted).startsWith("v1:").doesNotContain("apple-refresh-token");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("apple-refresh-token");
    }

    @Test
    void rejectsTamperedCiphertext() {
        AppleRefreshTokenCipher cipher = new AppleRefreshTokenCipher(KEY, new SecureRandom());
        String encrypted = cipher.encrypt("apple-refresh-token");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(BusinessBaseException.class)
                .hasMessageContaining("복호화");
    }
}
