package com.neogulmap.neogul_map.config.security.oauth;

import com.neogulmap.neogul_map.domain.User;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NativeOAuthCodeStore {

    private static final long CODE_TTL_SECONDS = 180;

    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public NativeOAuthCodeStore() {
        this(Clock.systemUTC());
    }

    NativeOAuthCodeStore(Clock clock) {
        this.clock = clock;
    }

    public String issue(String accessToken, String refreshToken, User user, String codeChallenge, String codeChallengeMethod) {
        purgeExpired();
        String code = UUID.randomUUID().toString();
        entries.put(code, Entry.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileComplete(user.isProfileComplete())
                .codeChallenge(codeChallenge)
                .codeChallengeMethod(normalizeMethod(codeChallengeMethod))
                .expiresAt(Instant.now(clock).plusSeconds(CODE_TTL_SECONDS))
                .build());
        return code;
    }

    public Optional<Entry> consume(String code, String codeVerifier) {
        if (code == null || code.isBlank() || codeVerifier == null || codeVerifier.isBlank()) {
            return Optional.empty();
        }

        Entry entry = entries.remove(code.trim());
        if (entry == null || entry.isExpired(Instant.now(clock))) {
            return Optional.empty();
        }

        return entry.matchesVerifier(codeVerifier.trim()) ? Optional.of(entry) : Optional.empty();
    }

    private void purgeExpired() {
        Instant now = Instant.now(clock);
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "S256";
        }
        return method.trim().toUpperCase();
    }

    private static String s256(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("PKCE S256 계산 실패", ex);
        }
    }

    @Getter
    @Builder
    public static class Entry {
        private String accessToken;
        private String refreshToken;
        private Long userId;
        private String email;
        private String nickname;
        private boolean profileComplete;
        private String codeChallenge;
        private String codeChallengeMethod;
        private Instant expiresAt;

        private boolean isExpired(Instant now) {
            return expiresAt == null || !expiresAt.isAfter(now);
        }

        private boolean matchesVerifier(String codeVerifier) {
            if (codeChallenge == null || codeChallenge.isBlank()) {
                return false;
            }

            if ("PLAIN".equalsIgnoreCase(codeChallengeMethod)) {
                return codeChallenge.equals(codeVerifier);
            }

            return codeChallenge.equals(s256(codeVerifier));
        }
    }
}
