package com.neogulmap.neogul_map.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AppleClientSecretServiceTest {

    @Test
    void createsShortLivedEs256ClientSecretWithAppleClaims() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        AppleClientSecretService service = new AppleClientSecretService(
                true,
                "com.nugulmap.native",
                "TEAM123",
                "KEY123",
                "",
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Clock.fixed(now, java.time.ZoneOffset.UTC)
        );

        String clientSecret = service.createClientSecret();
        var parsed = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret);
        Claims claims = parsed.getPayload();

        assertThat(parsed.getHeader().getKeyId()).isEqualTo("KEY123");
        assertThat(claims.getIssuer()).isEqualTo("TEAM123");
        assertThat(claims.getSubject()).isEqualTo("com.nugulmap.native");
        assertThat(claims.getAudience()).contains("https://appleid.apple.com");
        assertThat(claims.getIssuedAt()).isEqualTo(java.util.Date.from(now));
        assertThat(claims.getExpiration()).isEqualTo(java.util.Date.from(now.plusSeconds(300)));
    }
}
