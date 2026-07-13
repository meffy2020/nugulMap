package com.neogulmap.neogul_map.service;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class AppleClientSecretService {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final boolean enabled;
    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final PrivateKey privateKey;
    private final Clock clock;

    @Autowired
    public AppleClientSecretService(
            @Value("${app.oauth2.apple.revocation.enabled:false}") boolean enabled,
            @Value("${app.oauth2.apple.client-id:com.nugulmap.native}") String clientId,
            @Value("${app.oauth2.apple.revocation.team-id:}") String teamId,
            @Value("${app.oauth2.apple.revocation.key-id:}") String keyId,
            @Value("${app.oauth2.apple.revocation.private-key:}") String privateKey,
            @Value("${app.oauth2.apple.revocation.private-key-base64:}") String privateKeyBase64
    ) {
        this(enabled, clientId, teamId, keyId, privateKey, privateKeyBase64, Clock.systemUTC());
    }

    AppleClientSecretService(
            boolean enabled,
            String clientId,
            String teamId,
            String keyId,
            String privateKey,
            String privateKeyBase64,
            Clock clock
    ) {
        this.enabled = enabled;
        this.clientId = normalized(clientId);
        this.teamId = normalized(teamId);
        this.keyId = normalized(keyId);
        this.clock = clock;

        if (enabled) {
            requireValue(this.clientId, "APPLE_CLIENT_ID");
            requireValue(this.teamId, "APPLE_TEAM_ID");
            requireValue(this.keyId, "APPLE_KEY_ID");
            this.privateKey = parsePrivateKey(privateKey, privateKeyBase64);
        } else {
            this.privateKey = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public String createClientSecret() {
        if (!enabled || privateKey == null) {
            throw new IllegalStateException("Apple token exchange is not configured");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .header()
                    .keyId(keyId)
                    .and()
                .issuer(teamId)
                .subject(clientId)
                .audience()
                    .add(APPLE_ISSUER)
                    .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private static PrivateKey parsePrivateKey(String privateKey, String privateKeyBase64) {
        try {
            byte[] der;
            if (privateKeyBase64 != null && !privateKeyBase64.isBlank()) {
                byte[] decoded = Base64.getDecoder().decode(privateKeyBase64.replaceAll("\\s+", ""));
                der = looksLikePem(decoded) ? pemToDer(new String(decoded, StandardCharsets.UTF_8)) : decoded;
            } else {
                requireValue(normalized(privateKey), "APPLE_PRIVATE_KEY or APPLE_PRIVATE_KEY_BASE64");
                der = pemToDer(privateKey);
            }
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Apple private key must be a valid PKCS#8 .p8 key", e);
        }
    }

    private static boolean looksLikePem(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII).contains("BEGIN PRIVATE KEY");
    }

    private static byte[] pemToDer(String pem) {
        String normalizedPem = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(normalizedPem);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireValue(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required when Apple revocation is enabled");
        }
    }
}
