package com.yantrago.api.config;

import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PostAuthRateLimitFilter;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TrustedProxyResolver;
import com.yantrago.api.service.AuditLogService;
import com.yantrago.api.service.OwnerContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security filter chain configuration.
 * - Stateless sessions (JWT-based)
 * - Permits: /api/v1/auth/**, /actuator/health, /swagger-ui/**, /v3/api-docs/**, /api/v1/health
 * - Requires authentication for everything else
 * - CORS enabled
 * - Filter order: RateLimitFilter → JwtAuthFilter → TenantContextFilter → UsernamePasswordAuthenticationFilter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthFilter jwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;
    private final RateLimitFilter rateLimitFilter;
    private final PostAuthRateLimitFilter postAuthRateLimitFilter;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final TrustedProxyResolver trustedProxyResolver;
    private final OwnerContextService ownerContextService;
    private final String allowedOriginsRaw;
    private final String activeProfile;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          TenantContextFilter tenantContextFilter,
                          RateLimitFilter rateLimitFilter,
                          PostAuthRateLimitFilter postAuthRateLimitFilter,
                          ObjectProvider<AuditLogService> auditLogServiceProvider,
                          TrustedProxyResolver trustedProxyResolver,
                          OwnerContextService ownerContextService,
                          @Value("${security.cors.allowed-origins:}") String allowedOriginsRaw,
                          @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.postAuthRateLimitFilter = postAuthRateLimitFilter;
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.trustedProxyResolver = trustedProxyResolver;
        this.ownerContextService = ownerContextService;
        this.allowedOriginsRaw = allowedOriginsRaw;
        this.activeProfile = activeProfile;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // SEC-13: explicit response security headers. CSP is profile-aware —
            // strict in prod (no HTML endpoints are served), permissive enough
            // for Swagger UI in non-prod profiles.
            .headers(headers -> {
                headers.frameOptions(frame -> frame.deny());
                headers.contentTypeOptions(contentType -> {});
                headers.referrerPolicy(referrer -> referrer
                        .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.NO_REFERRER));
                headers.permissionsPolicy(permissions -> permissions
                        .policy("geolocation=(), camera=(), microphone=(), payment=()"));
                headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000));
                headers.contentSecurityPolicy(csp -> csp
                        .policyDirectives(isProduction()
                                ? "default-src 'none'; frame-ancestors 'none'"
                                : "default-src 'self'; img-src 'self' data:; "
                                + "style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
                                + "connect-src 'self'"));
            })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/logout-all").authenticated();
                auth.requestMatchers("/api/v1/auth/**").permitAll();
                auth.requestMatchers("/actuator/health").permitAll();
                auth.requestMatchers("/actuator/info").permitAll();
                // Prometheus/metrics are scrape endpoints — the network layer
                // (Nginx IP allowlist, firewall, loopback binding) restricts
                // /actuator/ to internal callers.
                auth.requestMatchers("/actuator/prometheus").permitAll();
                auth.requestMatchers("/actuator/metrics").permitAll();
                auth.requestMatchers("/api/v1/health").permitAll();
                if (!isProduction()) {
                    auth.requestMatchers("/swagger-ui/**").permitAll();
                    auth.requestMatchers("/swagger-ui.html").permitAll();
                    auth.requestMatchers("/v3/api-docs/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // SEC-06: Post-auth user rate limit runs after JwtAuthFilter
            // so the SecurityContext is populated with the user UUID.
            .addFilterAfter(postAuthRateLimitFilter, JwtAuthFilter.class)
            .addFilterAfter(tenantContextFilter, JwtAuthFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                // SEC-14: audit filter-layer 403 denials on sensitive paths —
                // these never reach AuditLogInterceptor.
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    auditFilterLayerDenial(request, HttpServletResponse.SC_FORBIDDEN);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                })
            );

        return http.build();
    }

    private boolean isProduction() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }

    /**
     * SEC-14: records a DENIED audit entry for sensitive-path requests that
     * were rejected inside the security filter chain (before the dispatcher
     * and its interceptors run). Best-effort — never breaks the response.
     */
    private void auditFilterLayerDenial(HttpServletRequest request, int status) {
        try {
            if (!AuditLogInterceptor.isSensitiveRequest(request.getMethod(), request.getRequestURI())) {
                return;
            }
            AuditLogService svc = auditLogServiceProvider.getIfAvailable();
            if (svc == null) {
                return;
            }
            UUID userId = null;
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UUID uuid) {
                userId = uuid;
            }
            String ip = trustedProxyResolver.resolveClientIp(request);
            String correlationId = request.getHeader("X-Request-ID");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            String path = request.getRequestURI()
                    .replace("\\", "\\\\").replace("\"", "\\\"");
            String details = "{\"method\":\"" + request.getMethod() + "\""
                    + ",\"path\":\"" + path + "\""
                    + ",\"status\":" + status
                    + ",\"outcome\":\"DENIED\""
                    + ",\"correlationId\":\"" + correlationId + "\""
                    + ",\"layer\":\"security-filter\"}";
            svc.recordAuditLog("SECURITY_DENIAL", "API", null,
                    ownerContextService.getOrganizationIdOrNull(), userId,
                    ip, null, details);
        } catch (Exception e) {
            log.error("Failed to persist filter-layer denial audit for {} {}",
                    request.getMethod(), request.getRequestURI(), e);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Prevent Spring Boot from auto-registering TenantContextFilter as a regular servlet filter.
     * It should only run inside the Spring Security filter chain, after JwtAuthFilter.
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevent Spring Boot from auto-registering JwtAuthFilter as a regular servlet filter.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevent Spring Boot from auto-registering RateLimitFilter as a regular servlet filter.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevent Spring Boot from auto-registering PostAuthRateLimitFilter as a regular servlet filter.
     */
    @Bean
    public FilterRegistrationBean<PostAuthRateLimitFilter> postAuthRateLimitFilterRegistration(
            PostAuthRateLimitFilter filter) {
        FilterRegistrationBean<PostAuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            // SEC-14: audit filter-layer 401 denials on sensitive paths.
            auditFilterLayerDenial(request, HttpServletResponse.SC_UNAUTHORIZED);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            log.warn("security.cors.allowed-origins is empty — all cross-origin requests will be rejected");
        }

        // SEC-13: use explicit origins, not wildcard patterns, when credentials are enabled.
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
