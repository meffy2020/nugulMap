package com.neogulmap.neogul_map.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PublicInsightRateLimiterTest {

    @Test
    void rejectsRequestsBeyondPerClientWindow() {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(2, 60, 100);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest request = requestFrom("198.51.100.10");

        assertThat(limiter.tryAcquire(request).allowed()).isTrue();
        assertThat(limiter.tryAcquire(request).allowed()).isTrue();
        PublicInsightRateLimiter.Decision denied = limiter.tryAcquire(request);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void doesNotTrustClientSuppliedForwardedForHeader() {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(1, 60, 100);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest first = requestFrom("198.51.100.10");
        first.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletRequest forged = requestFrom("198.51.100.10");
        forged.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(limiter.tryAcquire(first).allowed()).isTrue();
        assertThat(limiter.tryAcquire(forged).allowed()).isFalse();
    }

    @Test
    void boundsTrackedClientKeysWithSharedOverflowBucket() {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(1, 60, 1);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire(requestFrom("198.51.100.1")).allowed()).isTrue();
        assertThat(limiter.tryAcquire(requestFrom("198.51.100.2")).allowed()).isTrue();
        assertThat(limiter.tryAcquire(requestFrom("198.51.100.3")).allowed()).isFalse();
    }

    private static MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
