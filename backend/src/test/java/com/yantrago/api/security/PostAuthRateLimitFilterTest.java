package com.yantrago.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * SEC-06: Tests for PostAuthRateLimitFilter.
 *
 * Covers:
 * - Authenticated user is limited at 60 requests/minute
 * - Unauthenticated requests pass through (no user limit)
 * - Different users get separate buckets
 * - 429 response includes Retry-After header
 */
class PostAuthRateLimitFilterTest {

    private final PostAuthRateLimitFilter filter = new PostAuthRateLimitFilter();
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletResponse doRequest(UUID userId) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setRequestURI("/api/v1/machines");
        MockHttpServletResponse response = new MockHttpServletResponse();

        if (userId != null) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filter.doFilterInternal(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("SEC-06: Authenticated user is limited at 60 requests/minute")
    void authenticatedUser_limitedAt60() throws Exception {
        UUID userId = UUID.randomUUID();
        try {
            int lastStatus = 200;
            for (int i = 0; i < 65; i++) {
                lastStatus = doRequest(userId).getStatus();
            }
            assertEquals(429, lastStatus,
                    "Requests beyond the per-user limit must return 429");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("SEC-06: Unauthenticated requests pass through (no user limit)")
    void unauthenticated_passesThrough() throws Exception {
        try {
            for (int i = 0; i < 100; i++) {
                assertEquals(200, doRequest(null).getStatus(),
                        "Unauthenticated requests should not be user-limited");
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("SEC-06: Different users get separate buckets")
    void differentUsers_separateBuckets() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        try {
            // 50 requests from user1, 50 from user2 — both under the 60/min limit
            for (int i = 0; i < 50; i++) {
                assertEquals(200, doRequest(user1).getStatus());
            }
            for (int i = 0; i < 50; i++) {
                assertEquals(200, doRequest(user2).getStatus());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("SEC-06: 429 response includes Retry-After header")
    void rateLimitedResponse_includesRetryAfter() throws Exception {
        UUID userId = UUID.randomUUID();
        try {
            MockHttpServletResponse response = null;
            for (int i = 0; i < 65; i++) {
                response = doRequest(userId);
            }
            assertEquals(429, response.getStatus());
            String retryAfter = response.getHeader("Retry-After");
            assert retryAfter != null : "Retry-After header must be present";
            assert Long.parseLong(retryAfter) >= 1 : "Retry-After must be at least 1 second";
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
