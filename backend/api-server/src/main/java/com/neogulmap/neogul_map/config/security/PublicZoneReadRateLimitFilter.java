package com.neogulmap.neogul_map.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-instance abuse protection for public smoking-zone reads. Client supplied
 * forwarding headers are deliberately ignored; trusted proxy handling belongs
 * to the servlet container boundary.
 */
public final class PublicZoneReadRateLimitFilter extends OncePerRequestFilter {

    private static final String UNKNOWN_CLIENT = "unknown";
    private static final String OVERFLOW_CLIENT = "overflow";

    private final boolean enabled;
    private final int requestsPerWindow;
    private final long windowSeconds;
    private final int maxTrackedClients;
    private final ConcurrentHashMap<String, ClientWindow> windows = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochSecond = new AtomicLong();
    private Clock clock = Clock.systemUTC();

    public PublicZoneReadRateLimitFilter(
            boolean enabled,
            int requestsPerWindow,
            long windowSeconds,
            int maxTrackedClients
    ) {
        this.enabled = enabled;
        this.requestsPerWindow = Math.max(1, requestsPerWindow);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxTrackedClients = Math.max(1, maxTrackedClients);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || request == null || !"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = applicationPath(request);
        return !(path.equals("/zones") || path.startsWith("/zones/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Decision decision = tryAcquire(request);
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    Decision tryAcquire(HttpServletRequest request) {
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
                attempt.retryAfterSeconds = Math.max(
                        1,
                        current.expiresAt().getEpochSecond() - now.getEpochSecond()
                );
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
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank() || remoteAddress.length() > 128) {
            return UNKNOWN_CLIENT;
        }
        return remoteAddress.trim();
    }

    private void cleanExpiredWindows(Instant now) {
        long currentEpochSecond = now.getEpochSecond();
        long scheduled = nextCleanupEpochSecond.get();
        if (currentEpochSecond < scheduled
                || !nextCleanupEpochSecond.compareAndSet(scheduled, currentEpochSecond + windowSeconds)) {
            return;
        }
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String applicationPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record ClientWindow(int count, Instant expiresAt) {
    }

    private static final class Attempt {
        private boolean allowed;
        private long retryAfterSeconds;
    }
}
