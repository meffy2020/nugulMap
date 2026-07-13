package com.neogulmap.neogul_map.config.security.jwt;

import com.neogulmap.neogul_map.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Base64;

@Slf4j
@Component
public class TokenProvider {
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey key;
    private final long tokenValidityInMilliseconds;

    public TokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.token-validity-in-seconds}") long tokenValidityInSeconds) {
        // Base64로 인코딩된 secret을 디코딩
        byte[] decodedSecret = Base64.getDecoder().decode(secret.trim());
        this.key = Keys.hmacShaKeyFor(decodedSecret);
        this.tokenValidityInMilliseconds = tokenValidityInSeconds * 1000;
    }

    public String generateAccessToken(User user, Duration duration) {
        return generateToken(user, duration, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(User user, Duration duration) {
        return generateToken(user, duration, REFRESH_TOKEN_TYPE);
    }

    private String generateToken(User user, Duration duration, String tokenType) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + duration.toMillis());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);
    }

    public boolean validAccessToken(String token) {
        return validTokenOfType(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validRefreshToken(String token) {
        return validTokenOfType(token, REFRESH_TOKEN_TYPE);
    }

    public boolean validToken(String token) {
        return validAccessToken(token);
    }

    private boolean validTokenOfType(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public Authentication getAuthentication(String token) {
        if (!validAccessToken(token)) {
            throw new JwtException("Access token required");
        }
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return new UsernamePasswordAuthenticationToken(
            claims.getSubject(),
            null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
    
    /**
     * 토큰이 곧 만료되는지 확인 (5분 이내)
     * @param token JWT 토큰
     * @return 5분 이내 만료되면 true
     */
    public boolean isTokenExpiringSoon(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Date expiration = claims.getExpiration();
            long timeUntilExpiry = expiration.getTime() - System.currentTimeMillis();
            return timeUntilExpiry < 5 * 60 * 1000; // 5분
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true; // 에러 시 만료된 것으로 간주
        }
    }
}
