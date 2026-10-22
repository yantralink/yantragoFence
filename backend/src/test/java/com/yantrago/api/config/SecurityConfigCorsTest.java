package com.yantrago.api.config;

import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PostAuthRateLimitFilter;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TrustedProxyResolver;
import com.yantrago.api.service.AuditLogService;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * SEC-13: verifies CORS configuration uses an explicit origin allowlist —
 * no wildcard origins or origin patterns when credentials are enabled.
 */
class SecurityConfigCorsTest {

    private SecurityConfig configWith(String origins, String profile) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AuditLogService> provider = mock(ObjectProvider.class);
        return new SecurityConfig(
                mock(JwtAuthFilter.class),
                mock(TenantContextFilter.class),
                mock(RateLimitFilter.class),
                mock(PostAuthRateLimitFilter.class),
                provider,
                mock(TrustedProxyResolver.class),
                mock(OwnerContextService.class),
                origins,
                profile
        );
    }

    private CorsConfiguration resolveFor(SecurityConfig config, String path) {
        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return source.getCorsConfiguration(request);
    }

    @Test
    void cors_explicitOriginsAreAllowed() {
        SecurityConfig config = configWith(
                "https://admin.yantrago.com, https://app.yantrago.com", "prod");

        CorsConfiguration cors = resolveFor(config, "/api/v1/machines");

        assertNotNull(cors);
        assertEquals(2, cors.getAllowedOrigins().size());
        assertTrue(cors.getAllowedOrigins().contains("https://admin.yantrago.com"));
        assertTrue(cors.getAllowedOrigins().contains("https://app.yantrago.com"));
    }

    @Test
    void cors_noWildcardOriginPatterns() {
        SecurityConfig config = configWith("https://admin.yantrago.com", "prod");

        CorsConfiguration cors = resolveFor(config, "/api/v1/machines");

        assertNotNull(cors);
        // SEC-13: origin patterns must not be used with allowCredentials=true.
        assertNull(cors.getAllowedOriginPatterns());
        assertNull(cors.getAllowedOrigins() != null
                ? cors.getAllowedOrigins().stream().filter(o -> o.contains("*")).findFirst().orElse(null)
                : null);
    }

    @Test
    void cors_emptyOrigins_rejectsAllCrossOrigin() {
        SecurityConfig config = configWith("", "prod");

        CorsConfiguration cors = resolveFor(config, "/api/v1/machines");

        assertNotNull(cors);
        assertTrue(cors.getAllowedOrigins() == null || cors.getAllowedOrigins().isEmpty());
    }

    @Test
    void cors_noWildcardHeaders() {
        SecurityConfig config = configWith("https://admin.yantrago.com", "prod");

        CorsConfiguration cors = resolveFor(config, "/api/v1/machines");

        assertNotNull(cors);
        assertTrue(cors.getAllowedHeaders().stream().noneMatch("*"::equals));
    }

    @Test
    void cors_credentialsEnabledWithExplicitOriginsOnly() {
        SecurityConfig config = configWith("https://admin.yantrago.com", "prod");

        CorsConfiguration cors = resolveFor(config, "/api/v1/machines");

        assertNotNull(cors);
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
        // Credentials + wildcard is the dangerous combination — origins must be explicit.
        assertTrue(cors.getAllowedOrigins().stream().noneMatch(o -> o.contains("*")));
    }
}
