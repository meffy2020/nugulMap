package com.neogulmap.neogul_map.config.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SensitiveEndpointRateLimitFilterTest {

    @Test
    void rejectsAuthenticationExchangeBeyondPerClientWindow() throws Exception {
        SensitiveEndpointRateLimitFilter filter = filter(2, 60, 100);
        MockHttpServletRequest first = authRequest("198.51.100.10");
        MockHttpServletRequest second = authRequest("198.51.100.10");
        MockHttpServletRequest third = authRequest("198.51.100.10");
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, chain);
        filter.doFilter(second, secondResponse, chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(third, rejected, chain);

        verify(chain).doFilter(first, firstResponse);
        verify(chain).doFilter(second, secondResponse);
        verify(chain, never()).doFilter(third, rejected);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void doesNotTrustClientSuppliedForwardedFor() throws Exception {
        SensitiveEndpointRateLimitFilter filter = filter(1, 60, 100);
        MockHttpServletRequest first = authRequest("198.51.100.10");
        first.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletRequest second = authRequest("198.51.100.10");
        second.addHeader("X-Forwarded-For", "203.0.113.99");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(first, new MockHttpServletResponse(), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(second, rejected, chain);

        assertThat(rejected.getStatus()).isEqualTo(429);
    }

    @Test
    void rateLimitsOperatorEndpointsButSkipsOrdinaryPublicReads() throws Exception {
        SensitiveEndpointRateLimitFilter filter = filter(1, 60, 100);
        MockHttpServletRequest operator = new MockHttpServletRequest("GET", "/operator/moderation/reports");
        operator.setServletPath("/operator/moderation/reports");
        operator.setRemoteAddr("198.51.100.20");
        MockHttpServletRequest ordinary = new MockHttpServletRequest("GET", "/zones");
        ordinary.setServletPath("/zones");
        ordinary.setRemoteAddr("198.51.100.20");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(operator, new MockHttpServletResponse(), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(operator, rejected, chain);
        MockHttpServletResponse ordinaryResponse = new MockHttpServletResponse();
        filter.doFilter(ordinary, ordinaryResponse, chain);

        assertThat(rejected.getStatus()).isEqualTo(429);
        verify(chain).doFilter(ordinary, ordinaryResponse);
    }

    @Test
    void boundsTrackedClientsWithSharedOverflowBucket() {
        SensitiveEndpointRateLimitFilter filter = filter(1, 60, 1);

        assertThat(filter.tryAcquire(authRequest("198.51.100.1")).allowed()).isTrue();
        assertThat(filter.tryAcquire(authRequest("198.51.100.2")).allowed()).isTrue();
        assertThat(filter.tryAcquire(authRequest("198.51.100.3")).allowed()).isFalse();
    }

    private SensitiveEndpointRateLimitFilter filter(int limit, long windowSeconds, int maxClients) {
        SensitiveEndpointRateLimitFilter filter = new SensitiveEndpointRateLimitFilter(
                true,
                limit,
                windowSeconds,
                maxClients
        );
        ReflectionTestUtils.setField(
                filter,
                "clock",
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC)
        );
        return filter;
    }

    private MockHttpServletRequest authRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/mobile/exchange");
        request.setServletPath("/auth/mobile/exchange");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
