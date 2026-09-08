package com.neogulmap.neogul_map.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void keepsInsightAndRecommendationReadBudgetsIndependent() {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(1, 60, 100);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest insight = requestFrom("198.51.100.10", "/insights/map");
        MockHttpServletRequest recommendation = requestFrom("198.51.100.10", "/recommendations/places");

        assertThat(limiter.tryAcquire(insight).allowed()).isTrue();
        assertThat(limiter.tryAcquire(insight).allowed()).isFalse();
        assertThat(limiter.tryAcquire(recommendation).allowed()).isTrue();
        assertThat(limiter.tryAcquire(recommendation).allowed()).isFalse();
    }

    @Test
    void weightedPermitIsAllOrNothing() {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(5, 60, 100);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest recommendation = requestFrom("198.51.100.10", "/recommendations/places");

        assertThat(limiter.tryAcquire(recommendation, 4).allowed()).isTrue();
        assertThat(limiter.tryAcquire(recommendation, 2).allowed()).isFalse();
        assertThat(limiter.tryAcquire(recommendation).allowed()).isTrue();
        assertThat(limiter.tryAcquire(recommendation).allowed()).isFalse();
        assertThatThrownBy(() -> limiter.tryAcquire(recommendation, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weightedPermitRemainsAtomicUnderConcurrency() throws Exception {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(4, 60, 100);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest recommendation = requestFrom("198.51.100.10", "/recommendations/places");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAcquire(recommendation, 4).allowed();
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isOne();
        }
    }

    @Test
    void overflowClientsStillUseIndependentEndpointBuckets() {
        PublicInsightRateLimiter limiter = new PublicInsightRateLimiter(1, 60, 1);
        ReflectionTestUtils.setField(limiter, "clock", Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest firstInsight = requestFrom("198.51.100.1", "/insights/map");
        MockHttpServletRequest overflowInsight = requestFrom("198.51.100.2", "/insights/map");
        MockHttpServletRequest overflowRecommendation = requestFrom("198.51.100.2", "/recommendations/places");

        assertThat(limiter.tryAcquire(firstInsight).allowed()).isTrue();
        assertThat(limiter.tryAcquire(overflowInsight).allowed()).isTrue();
        assertThat(limiter.tryAcquire(overflowRecommendation).allowed()).isTrue();
        assertThat(limiter.tryAcquire(overflowInsight).allowed()).isFalse();
        assertThat(limiter.tryAcquire(overflowRecommendation).allowed()).isFalse();
    }

    private static MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static MockHttpServletRequest requestFrom(String remoteAddress, String servletPath) {
        MockHttpServletRequest request = requestFrom(remoteAddress);
        request.setServletPath(servletPath);
        return request;
    }
}
