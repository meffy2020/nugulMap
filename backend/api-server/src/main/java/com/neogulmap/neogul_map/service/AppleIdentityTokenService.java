package com.neogulmap.neogul_map.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class AppleIdentityTokenService {
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final NimbusJwtDecoder decoder;
    private final String expectedAudience;

    public AppleIdentityTokenService(
            @Value("${app.oauth2.apple.client-id:com.nugulmap.native}") String expectedAudience) {
        this.decoder = NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys").build();
        this.expectedAudience = expectedAudience;
    }

    public AppleIdentity verify(String identityToken) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new JwtException("Apple identity token is required.");
        }

        Jwt jwt = decoder.decode(identityToken);
        if (jwt.getIssuer() == null || !APPLE_ISSUER.equals(jwt.getIssuer().toString())) {
            throw new JwtException("Unexpected Apple identity token issuer.");
        }

        if (!jwt.getAudience().contains(expectedAudience)) {
            throw new JwtException("Unexpected Apple identity token audience.");
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Apple identity token subject is missing.");
        }

        return new AppleIdentity(subject, jwt.getClaimAsString("email"));
    }

    @Getter
    public static class AppleIdentity {
        private final String subject;
        private final String email;

        AppleIdentity(String subject, String email) {
            this.subject = subject;
            this.email = email;
        }
    }
}
