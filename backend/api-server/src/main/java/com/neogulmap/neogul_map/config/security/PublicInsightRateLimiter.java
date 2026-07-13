package com.neogulmap.neogul_map.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublicInsightRateLimiter {

    private static final String UNKNOWN_CLIENT = "unknown";
    private static final String OVERFLOW_CLIENT = "overflow";

    private final int requestsPerWindow;
    private final long windowSeconds;
    private final int maxTrackedClients;
    private final ConcurrentHashMap<String, ClientWindow> windows = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochSecond = new AtomicLong();
    private Clock clock = Clock.systemUTC();

    public PublicInsightRateLimiter(
            @Value("${app.insights.public-rate-limit.requests-per-window:120}") int requestsPerWindow,
            @Value("${app.insights.public-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.insights.public-rate-limit.max-tracked-clients:10000}") int maxTrackedClients
    ) {
        this.requestsPerWindow = Math.max(1, requestsPerWindow);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxTrackedClients = Math.max(1, maxTrackedClients);
    }

    public Decision tryAcquire(HttpServletRequest request) {
        Instant now = Instant.now(clock);
        cleanExpiredWindows(now);
        String clientKey = clientKey(request);
        if (!windows.containsKey(clientKey) && windows.size() >= maxTrackedClients) {
            clientKey = OVERFLOW_CLIENT;
        }

        Attempt attempt = new Attempt();
        windows.compute(clientKey, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                attempt.allowed = true;
                return new ClientWindow(1, now.plusSeconds(windowSeconds));
            }
            if (current.count() >= requestsPerWindow) {
                attempt.retryAfterSeconds = retryAfterSeconds(current.expiresAt(), now);
                return current;
            }
            attempt.allowed = true;
            return new ClientWindow(current.count() + 1, current.expiresAt());
        });

        return attempt.allowed
                ? new Decision(true, 0)
                : new Decision(false, Math.max(1, attempt.retryAfterSeconds));
    }

    private String clientKey(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return UNKNOWN_CLIENT;
        }
        // X-Forwarded-For is intentionally not parsed here. The servlet container may
        // resolve forwarding headers only within its configured trusted-proxy boundary.
        String remoteAddress = request.getRemoteAddr().trim();
        return remoteAddress.length() <= 128 ? remoteAddress : UNKNOWN_CLIENT;
    }

    private void cleanExpiredWindows(Instant now) {
        long nowEpochSecond = now.getEpochSecond();
        long scheduled = nextCleanupEpochSecond.get();
        if (nowEpochSecond < scheduled
                || !nextCleanupEpochSecond.compareAndSet(scheduled, nowEpochSecond + windowSeconds)) {
            return;
        }
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private long retryAfterSeconds(Instant expiresAt, Instant now) {
        long seconds = expiresAt.getEpochSecond() - now.getEpochSecond();
        return Math.max(1, seconds);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record ClientWindow(int count, Instant expiresAt) {
    }

    private static final class Attempt {
        private boolean allowed;
        private long retryAfterSeconds;
    }
}
