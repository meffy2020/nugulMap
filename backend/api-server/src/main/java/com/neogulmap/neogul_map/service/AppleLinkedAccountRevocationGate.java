package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 신규 Apple 계정은 로그인 때 저장한 refresh token으로 Apple 연결을 먼저 해제합니다.
 * 토큰 저장 도입 전에 생성된 계정은 Apple 지침에 따라 로컬 데이터 삭제를 막지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleLinkedAccountRevocationGate implements LinkedAccountRevocationService {

    private final AppleRefreshTokenCipher refreshTokenCipher;
    private final AppleTokenEndpointService tokenEndpointService;

    @Override
    public void revokeBeforeDeletion(User user) {
        if (user == null || !"apple".equalsIgnoreCase(user.getOauthProvider())) {
            return;
        }

        String encryptedRefreshToken = user.getAppleRefreshTokenCiphertext();
        if (encryptedRefreshToken == null || encryptedRefreshToken.isBlank()) {
            log.warn("Legacy Apple account has no stored refresh token; local deletion continues for userId={}", user.getId());
            return;
        }

        String refreshToken = refreshTokenCipher.decrypt(encryptedRefreshToken);
        tokenEndpointService.revokeRefreshToken(refreshToken);
        user.setAppleRefreshTokenCiphertext(null);
    }
}
