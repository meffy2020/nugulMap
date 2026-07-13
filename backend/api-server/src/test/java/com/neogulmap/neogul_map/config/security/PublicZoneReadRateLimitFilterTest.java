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

class PublicZoneReadRateLimitFilterTest {

    @Test
    void rejectsPublicZoneReadsBeyondPerClientWindow() throws Exception {
        PublicZoneReadRateLimitFilter filter = filter(2, 60, 100);
        MockHttpServletRequest first = zoneRequest("/zones/bounds", "198.51.100.10");
        MockHttpServletRequest second = zoneRequest("/zones/bounds", "198.51.100.10");
        MockHttpServletRequest third = zoneRequest("/zones/bounds", "198.51.100.10");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        MockHttpServletResponse rejected = new MockHttpServletResponse();

        filter.doFilter(first, firstResponse, chain);
        filter.doFilter(second, secondResponse, chain);
        filter.doFilter(third, rejected, chain);

        verify(chain).doFilter(first, firstResponse);
        verify(chain).doFilter(second, secondResponse);
        verify(chain, never()).doFilter(third, rejected);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void appliesToZoneRootAndDetailsButSkipsNonZoneAndMutatingRequests() throws Exception {
        PublicZoneReadRateLimitFilter filter = filter(1, 60, 100);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest root = zoneRequest("/zones", "198.51.100.20");
        MockHttpServletRequest detail = zoneRequest("/zones/10", "198.51.100.20");
        MockHttpServletRequest insights = zoneRequest("/insights/map", "198.51.100.20");
        MockHttpServletRequest mutation = new MockHttpServletRequest("POST", "/zones");
        mutation.setServletPath("/zones");
        mutation.setRemoteAddr("198.51.100.20");
        MockHttpServletResponse rootResponse = new MockHttpServletResponse();

        filter.doFilter(root, rootResponse, chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(detail, rejected, chain);
        MockHttpServletResponse insightsResponse = new MockHttpServletResponse();
        filter.doFilter(insights, insightsResponse, chain);
        MockHttpServletResponse mutationResponse = new MockHttpServletResponse();
        filter.doFilter(mutation, mutationResponse, chain);

        assertThat(rejected.getStatus()).isEqualTo(429);
        verify(chain).doFilter(root, rootResponse);
        verify(chain).doFilter(insights, insightsResponse);
        verify(chain).doFilter(mutation, mutationResponse);
    }

    @Test
    void doesNotTrustClientSuppliedForwardedFor() throws Exception {
        PublicZoneReadRateLimitFilter filter = filter(1, 60, 100);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest first = zoneRequest("/zones", "198.51.100.10");
        first.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletRequest forged = zoneRequest("/zones", "198.51.100.10");
        forged.addHeader("X-Forwarded-For", "203.0.113.99");

        filter.doFilter(first, new MockHttpServletResponse(), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(forged, rejected, chain);

        assertThat(rejected.getStatus()).isEqualTo(429);
    }

    @Test
    void boundsTrackedClientsWithSharedOverflowBucket() {
        PublicZoneReadRateLimitFilter filter = filter(1, 60, 1);

        assertThat(filter.tryAcquire(zoneRequest("/zones", "198.51.100.1")).allowed()).isTrue();
        assertThat(filter.tryAcquire(zoneRequest("/zones", "198.51.100.2")).allowed()).isTrue();
        assertThat(filter.tryAcquire(zoneRequest("/zones", "198.51.100.3")).allowed()).isFalse();
    }

    private PublicZoneReadRateLimitFilter filter(int limit, long windowSeconds, int maxClients) {
        PublicZoneReadRateLimitFilter filter = new PublicZoneReadRateLimitFilter(
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

    private MockHttpServletRequest zoneRequest(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
