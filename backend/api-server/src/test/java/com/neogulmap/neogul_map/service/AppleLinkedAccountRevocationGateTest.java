package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppleLinkedAccountRevocationGateTest {

    private final AppleRefreshTokenCipher cipher = mock(AppleRefreshTokenCipher.class);
    private final AppleTokenEndpointService endpoint = mock(AppleTokenEndpointService.class);
    private final AppleLinkedAccountRevocationGate gate = new AppleLinkedAccountRevocationGate(cipher, endpoint);

    @Test
    void revokesStoredAppleRefreshTokenBeforeLocalDeletion() {
        User user = User.builder()
                .id(1L)
                .oauthProvider("apple")
                .appleRefreshTokenCiphertext("encrypted")
                .build();
        when(cipher.decrypt("encrypted")).thenReturn("refresh-token");

        gate.revokeBeforeDeletion(user);

        verify(endpoint).revokeRefreshToken("refresh-token");
        assertThat(user.getAppleRefreshTokenCiphertext()).isNull();
    }

    @Test
    void legacyAppleAccountWithoutStoredTokenStillAllowsLocalDeletion() {
        User user = User.builder().id(2L).oauthProvider("apple").build();

        gate.revokeBeforeDeletion(user);

        verify(endpoint, never()).revokeRefreshToken(org.mockito.ArgumentMatchers.anyString());
    }
}
