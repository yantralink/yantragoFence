package com.yantrago.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SEC-06: Tests for TrustedProxyResolver.
 *
 * Covers:
 * - Direct (non-proxy) request uses remoteAddr
 * - Trusted proxy with X-Forwarded-For uses the header value
 * - Spoofed X-Forwarded-For from untrusted source is ignored
 * - Multi-hop X-Forwarded-For chain walks right-to-left
 * - No X-Forwarded-For from trusted proxy uses remoteAddr
 * - Empty/blank X-Forwarded-For uses remoteAddr
 * - Custom trusted proxy list
 */
class TrustedProxyResolverTest {

    private MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        return req;
    }

    @Test
    @DisplayName("Direct request (no proxy) uses remoteAddr")
    void directRequest_usesRemoteAddr() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("203.0.113.7",
                resolver.resolveClientIp(request("203.0.113.7", null)));
    }

    @Test
    @DisplayName("Trusted proxy with X-Forwarded-For uses header value")
    void trustedProxy_usesXff() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("203.0.113.7",
                resolver.resolveClientIp(request("127.0.0.1", "203.0.113.7")));
    }

    @Test
    @DisplayName("Untrusted source with spoofed X-Forwarded-For ignores header")
    void untrustedSource_ignoresXff() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("10.0.0.5",
                resolver.resolveClientIp(request("10.0.0.5", "203.0.113.7")));
    }

    @Test
    @DisplayName("Multi-hop chain: walks right-to-left, first non-trusted is client")
    void multiHopChain_walksRightToLeft() {
        // Chain: client=203.0.113.7, proxy1=10.0.0.1 (untrusted), proxy2=127.0.0.1 (trusted)
        // Connection comes from 127.0.0.1 (trusted), so we inspect the header.
        // Walking right-to-left: 10.0.0.1 is not trusted → that's the client.
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("10.0.0.1",
                resolver.resolveClientIp(request("127.0.0.1", "203.0.113.7, 10.0.0.1")));
    }

    @Test
    @DisplayName("All-trusted chain: uses leftmost (original client)")
    void allTrustedChain_usesLeftmost() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1,10.0.0.1");
        assertEquals("203.0.113.7",
                resolver.resolveClientIp(request("127.0.0.1", "203.0.113.7, 10.0.0.1")));
    }

    @Test
    @DisplayName("Trusted proxy with no X-Forwarded-For uses remoteAddr")
    void trustedProxy_noXff_usesRemoteAddr() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("127.0.0.1",
                resolver.resolveClientIp(request("127.0.0.1", null)));
    }

    @Test
    @DisplayName("Trusted proxy with blank X-Forwarded-For uses remoteAddr")
    void trustedProxy_blankXff_usesRemoteAddr() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("127.0.0.1",
                resolver.resolveClientIp(request("127.0.0.1", "  ")));
    }

    @Test
    @DisplayName("Trusted proxy with empty X-Forwarded-For entries uses remoteAddr")
    void trustedProxy_emptyEntries_usesRemoteAddr() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("127.0.0.1");
        assertEquals("127.0.0.1",
                resolver.resolveClientIp(request("127.0.0.1", ",,")));
    }

    @Test
    @DisplayName("IPv6 loopback trusted proxy")
    void ipv6Loopback_trusted() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("::1");
        assertEquals("203.0.113.7",
                resolver.resolveClientIp(request("::1", "203.0.113.7")));
    }

    @Test
    @DisplayName("Custom trusted proxy list")
    void customTrustedProxy() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("10.0.0.1");
        assertEquals("203.0.113.7",
                resolver.resolveClientIp(request("10.0.0.1", "203.0.113.7")));
    }

    @Test
    @DisplayName("Empty trusted proxy list ignores all headers")
    void emptyTrustedList_ignoresAllHeaders() {
        TrustedProxyResolver resolver = new TrustedProxyResolver("");
        assertEquals("10.0.0.5",
                resolver.resolveClientIp(request("10.0.0.5", "203.0.113.7")));
    }

    @Test
    @DisplayName("Null trusted proxy config ignores all headers")
    void nullTrustedConfig_ignoresAllHeaders() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(null);
        assertEquals("10.0.0.5",
                resolver.resolveClientIp(request("10.0.0.5", "203.0.113.7")));
    }
}
