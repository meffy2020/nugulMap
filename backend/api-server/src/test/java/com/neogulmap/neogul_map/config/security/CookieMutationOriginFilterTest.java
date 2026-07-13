package com.neogulmap.neogul_map.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CookieMutationOriginFilterTest {

    private final CookieMutationOriginFilter filter = new CookieMutationOriginFilter(
            true,
            new String[]{"https://nugulmap.com", "http://localhost:3000"}
    );

    @Test
    void allowsCookieMutationFromConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = cookieMutation("https://nugulmap.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsCookieMutationFromForeignOrigin() throws Exception {
        MockHttpServletRequest request = cookieMutation("https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_ORIGIN_DENIED");
    }

    @Test
    void rejectsCookieMutationWithoutBrowserOriginEvidence() throws Exception {
        MockHttpServletRequest request = cookieMutation(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsNativeBearerMutationWithoutOriginOrCookieContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/users/me");
        request.addHeader("Authorization", "Bearer native-token");
        request.setCookies(new Cookie("accessToken", "browser-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsUnauthenticatedAndSafeRequests() throws Exception {
        MockHttpServletRequest publicPost = new MockHttpServletRequest("POST", "/public/support/requests");
        MockHttpServletRequest cookieGet = new MockHttpServletRequest("GET", "/zones");
        cookieGet.setCookies(new Cookie("accessToken", "browser-token"));
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(publicPost, firstResponse, chain);
        filter.doFilter(cookieGet, secondResponse, chain);

        verify(chain).doFilter(publicPost, firstResponse);
        verify(chain).doFilter(cookieGet, secondResponse);
    }

    @Test
    void normalizesRefererToOrigin() throws Exception {
        MockHttpServletRequest request = cookieMutation(null);
        request.addHeader("Referer", "https://nugulmap.com/profile/settings?tab=security");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest cookieMutation(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/zones");
        request.setCookies(new Cookie("accessToken", "browser-token"));
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
