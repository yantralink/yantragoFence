package com.yantrago.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Tests the loopback skip in RateLimitFilter: all HTTP traffic arrives via the
 * local nginx reverse proxy with remoteAddr=127.0.0.1, so the per-IP bucket
 * must not throttle external users collectively.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletResponse doRequest(String remoteAddr) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        return response;
    }

    @Test
    void loopbackRequestsAreNeverIpLimited() throws Exception {
        for (int i = 0; i < 150; i++) {
            MockHttpServletResponse response = doRequest("127.0.0.1");
            assertEquals(200, response.getStatus(),
                    "loopback request " + i + " must not be IP-limited");
        }
    }

    @Test
    void externalIpIsLimitedAfter100RequestsPerMinute() throws Exception {
        int lastStatus = 200;
        for (int i = 0; i < 120; i++) {
            lastStatus = doRequest("203.0.113.7").getStatus();
        }
        assertEquals(429, lastStatus, "requests beyond the per-IP limit must return 429");
    }

    @Test
    void authenticatedUserIsLimitedAfter60RequestsPerMinute() throws Exception {
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            int lastStatus = 200;
            for (int i = 0; i < 70; i++) {
                lastStatus = doRequest("127.0.0.1").getStatus();
            }
            assertEquals(429, lastStatus, "requests beyond the per-user limit must return 429");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
