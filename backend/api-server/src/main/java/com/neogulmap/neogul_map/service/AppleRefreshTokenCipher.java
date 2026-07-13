package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AppleRefreshTokenCipher {

    private static final String VERSION = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    @Autowired
    public AppleRefreshTokenCipher(
            @Value("${app.oauth2.apple.revocation.token-encryption-key:}") String encodedKey
    ) {
        this(encodedKey, new SecureRandom());
    }

    AppleRefreshTokenCipher(String encodedKey, SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
        this.key = encodedKey == null || encodedKey.isBlank()
                ? null
                : new SecretKeySpec(decodeKey(encodedKey), "AES");
    }

    public String encrypt(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "Apple refresh token이 없어 계정 연결 해제를 준비할 수 없습니다."
            );
        }
        requireConfiguredKey();

        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new BusinessBaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    public String decrypt(String encryptedToken) {
        requireConfiguredKey();
        if (encryptedToken == null || !encryptedToken.startsWith(VERSION)) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "저장된 Apple 계정 연결 정보 형식이 올바르지 않습니다."
            );
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedToken.substring(VERSION.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new GeneralSecurityException("Encrypted payload is too short");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[payload.length - NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(payload, NONCE_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "저장된 Apple 계정 연결 정보를 복호화할 수 없습니다."
            );
        }
    }

    private void requireConfiguredKey() {
        if (key == null) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "Apple token 암호화 키가 구성되지 않았습니다."
            );
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            if (decoded.length != 32) {
                throw new IllegalArgumentException("Apple token encryption key must decode to 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "APPLE_TOKEN_ENCRYPTION_KEY must be a base64-encoded 32-byte key",
                    e
            );
        }
    }
}
