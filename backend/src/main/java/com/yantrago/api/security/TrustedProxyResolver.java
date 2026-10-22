package com.yantrago.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SEC-06: Resolves the real client IP address only through explicitly
 * trusted proxy hops.
 *
 * The X-Forwarded-For header is untrusted by default. Only when the
 * direct connection comes from a configured trusted proxy (e.g. the
 * local nginx reverse proxy) do we inspect the header — and only the
 * rightmost untrusted entries in the chain, walking backwards from
 * the trusted proxy.
 *
 * This prevents spoofing: a client sending a fake X-Forwarded-For
 * header through the trusted proxy cannot choose the resolved IP,
 * because we walk the chain right-to-left and stop at the first
 * non-trusted hop.
 *
 * Configuration:
 * - {@code security.trusted-proxies} (comma-separated IP/CIDR, default
 *   {@code 127.0.0.1,::1}) — addresses of reverse proxies that are
 *   allowed to set forwarding headers.
 */
@Component
public class TrustedProxyResolver {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyResolver.class);

    private final List<String> trustedProxies;

    public TrustedProxyResolver(
            @Value("${security.trusted-proxies:127.0.0.1,::1}") String trustedProxies) {
        this.trustedProxies = trustedProxies == null || trustedProxies.isBlank()
                ? Collections.emptyList()
                : Arrays.stream(trustedProxies.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        log.info("Trusted proxies configured: {}", this.trustedProxies);
    }

    /**
     * Resolves the real client IP for the given request.
     *
     * Algorithm:
     * 1. Start with the direct connection's remoteAddr.
     * 2. If remoteAddr is a trusted proxy, inspect X-Forwarded-For.
     * 3. Walk the X-Forwarded-For chain right-to-left; the first
     *    non-trusted address is the real client.
     * 4. If all entries in the chain are trusted, use the leftmost entry.
     *
     * This is the standard "trusted proxy" approach used by Express,
     * Django, and Spring's ForwardedHeaderFilter with a trusted proxy
     * list.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // If the direct connection is not from a trusted proxy, use
        // remoteAddr directly — ignore any forwarding headers.
        if (!isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        // The connection is from a trusted proxy. Inspect X-Forwarded-For.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            // Trusted proxy but no X-Forwarded-For — use remoteAddr.
            return remoteAddr;
        }

        // Parse the chain: client, proxy1, proxy2, ...
        // The leftmost is the original client; the rightmost is the
        // trusted proxy that sent the header to us.
        List<String> chain = Arrays.stream(xff.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (chain.isEmpty()) {
            return remoteAddr;
        }

        // Walk right-to-left, skipping trusted proxies. The first
        // non-trusted address is the real client.
        for (int i = chain.size() - 1; i >= 0; i--) {
            String hop = chain.get(i);
            if (!isTrusted(hop)) {
                return hop;
            }
        }

        // All hops are trusted — use the leftmost (original client).
        return chain.get(0);
    }

    /**
     * Returns true if the given IP is in the trusted proxy list.
     * Supports exact IP matching. CIDR support can be added if needed.
     */
    private boolean isTrusted(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return trustedProxies.contains(ip);
    }
}
