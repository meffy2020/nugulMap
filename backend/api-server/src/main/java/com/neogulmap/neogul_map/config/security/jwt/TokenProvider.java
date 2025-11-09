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
    private final SecretKey key;
    private final long tokenValidityInMilliseconds;

    public TokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.token-validity-in-seconds}") long tokenValidityInSeconds) {
        // Base64로 인코딩된 secret을 디코딩
        byte[] decodedSecret = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(decodedSecret);
        this.tokenValidityInMilliseconds = tokenValidityInSeconds * 1000;
    }

    public String generateToken(User user, Duration duration) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + duration.toMillis());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
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

    public boolean validToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
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

