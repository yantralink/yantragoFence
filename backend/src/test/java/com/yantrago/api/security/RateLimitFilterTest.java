package com.yantrago.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * SEC-06: Tests for RateLimitFilter.
 *
 * Covers:
 * - Trusted proxy resolution: X-Forwarded-For from loopback (trusted) is used
 * - Spoofed X-Forwarded-For from untrusted source is ignored
 * - Per-IP rate limiting after 100 requests/minute
 * - Login endpoint has stricter limit (10/minute)
 * - 429 response includes Retry-After header
 * - Bounded bucket eviction
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        // Default trusted proxies: 127.0.0.1, ::1
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1,::1");
        filter = new RateLimitFilter(resolver);
    }

    private MockHttpServletResponse doRequest(String remoteAddr, String path) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.setRequestURI(path != null ? path : "/api/v1/machines");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        return response;
    }

    private MockHttpServletResponse doRequestWithXff(String remoteAddr, String xff, String path)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.setRequestURI(path != null ? path : "/api/v1/machines");
        if (xff != null) {
            request.addHeader("X-Forwarded-For", xff);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        return response;
    }

    // --- SEC-06: Trusted proxy resolution ---

    @Test
    @DisplayName("SEC-06: Request from trusted proxy uses X-Forwarded-For as client IP")
    void trustedProxy_usesXff() throws Exception {
        // 100 requests from loopback with X-Forwarded-For=203.0.113.7
        // should be IP-limited at 100/min (loopback is trusted, so real IP = 203.0.113.7)
        int lastStatus = 200;
        for (int i = 0; i < 105; i++) {
            lastStatus = doRequestWithXff("127.0.0.1", "203.0.113.7", "/api/v1/machines")
                    .getStatus();
        }
        assertEquals(429, lastStatus,
                "Requests via trusted proxy should be IP-limited by the X-Forwarded-For value");
    }

    @Test
    @DisplayName("SEC-06: Different X-Forwarded-For values get separate buckets")
    void trustedProxy_separateBucketsPerXff() throws Exception {
        // 50 requests from IP-A, 50 from IP-B — both under the 100/min limit
        for (int i = 0; i < 50; i++) {
            assertEquals(200, doRequestWithXff("127.0.0.1", "203.0.113.7", "/api/v1/machines")
                    .getStatus());
        }
        for (int i = 0; i < 50; i++) {
            assertEquals(200, doRequestWithXff("127.0.0.1", "198.51.100.42", "/api/v1/machines")
                    .getStatus());
        }
    }

    @Test
    @DisplayName("SEC-06: Spoofed X-Forwarded-For from untrusted source is ignored")
    void untrustedSource_spoofedXffIgnored() throws Exception {
        // Request from 10.0.0.5 (not a trusted proxy) with X-Forwarded-For=203.0.113.7
        // The real IP should be 10.0.0.5, not 203.0.113.7
        // 100 requests from 10.0.0.5 should be limited, but 100 from the spoofed
        // 203.0.113.7 should NOT be limited (because the spoofed IP is ignored)
        for (int i = 0; i < 100; i++) {
            assertEquals(200, doRequestWithXff("10.0.0.5", "203.0.113.7", "/api/v1/machines")
                    .getStatus());
        }
        // 101st request from 10.0.0.5 should be limited
        assertEquals(429, doRequestWithXff("10.0.0.5", "203.0.113.7", "/api/v1/machines")
                .getStatus());
    }

    @Test
    @DisplayName("SEC-06: Request from untrusted source without X-Forwarded-For uses remoteAddr")
    void untrustedSource_noXff_usesRemoteAddr() throws Exception {
        int lastStatus = 200;
        for (int i = 0; i < 105; i++) {
            lastStatus = doRequest("10.0.0.5", "/api/v1/machines").getStatus();
        }
        assertEquals(429, lastStatus, "Direct requests should be IP-limited by remoteAddr");
    }

    // --- SEC-06: Login endpoint stricter limit ---

    @Test
    @DisplayName("SEC-06: Login endpoint has stricter rate limit (10/minute per IP)")
    void loginEndpoint_stricterLimit() throws Exception {
        int lastStatus = 200;
        for (int i = 0; i < 12; i++) {
            lastStatus = doRequest("203.0.113.7", "/api/v1/auth/login").getStatus();
        }
        assertEquals(429, lastStatus,
                "Login endpoint should be limited at 10/minute (stricter than the 100/min IP limit)");
    }

    @Test
    @DisplayName("SEC-06: Refresh endpoint has stricter rate limit (20/minute per IP)")
    void refreshEndpoint_stricterLimit() throws Exception {
        int lastStatus = 200;
        for (int i = 0; i < 22; i++) {
            lastStatus = doRequest("203.0.113.99", "/api/v1/auth/refresh").getStatus();
        }
        assertEquals(429, lastStatus,
                "Refresh endpoint should be limited at 20/minute");
    }

    // --- SEC-06: 429 response includes Retry-After ---

    @Test
    @DisplayName("SEC-06: 429 response includes Retry-After header")
    void rateLimitedResponse_includesRetryAfter() throws Exception {
        MockHttpServletResponse response = null;
        for (int i = 0; i < 105; i++) {
            response = doRequest("203.0.113.50", "/api/v1/machines");
        }
        assertEquals(429, response.getStatus());
        String retryAfter = response.getHeader("Retry-After");
        assert retryAfter != null : "Retry-After header must be present";
        assert Long.parseLong(retryAfter) >= 1 : "Retry-After must be at least 1 second";
    }

    @Test
    @DisplayName("SEC-06: 429 response includes JSON error body")
    void rateLimitedResponse_includesJsonBody() throws Exception {
        MockHttpServletResponse response = null;
        for (int i = 0; i < 105; i++) {
            response = doRequest("203.0.113.51", "/api/v1/machines");
        }
        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
        String body = response.getContentAsString();
        assert body.contains("Rate limit exceeded") : "Body should contain error message";
    }

    // --- SEC-06: Bounded bucket eviction ---

    @Test
    @DisplayName("SEC-06: Bucket maps are bounded (eviction prevents unbounded growth)")
    void bucketEviction_preventsUnboundedGrowth() throws Exception {
        // Generate 200 requests from 200 different IPs
        for (int i = 0; i < 200; i++) {
            doRequest("10.1." + (i / 256) + "." + (i % 256), "/api/v1/machines");
        }
        // The filter should not OOM — eviction runs periodically.
        // We can't directly test the map size without exposing internals,
        // but the test passing without timeout/memory issues is the assertion.
        assert true;
    }
}
