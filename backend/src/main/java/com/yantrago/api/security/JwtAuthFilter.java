package com.yantrago.api.security;

import com.yantrago.api.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Extracts JWT from the Authorization: Bearer header, validates it,
 * and sets the SecurityContext with the user's identity and authorities.
 * Runs once per request, before TenantContextFilter.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (!jwtService.validateAccessToken(token)) {
            log.debug("Invalid JWT token received from {}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseAccessToken(token);
            UUID userId = jwtService.extractUserId(claims);
            String email = claims.getSubject();
            String roles = jwtService.extractRoles(claims);

            List<SimpleGrantedAuthority> authorities;
            if (roles != null && !roles.isBlank()) {
                authorities = Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                        .collect(Collectors.toList());
            } else {
                authorities = Collections.emptyList();
            }

            // Simplified: store userId as principal, email as credentials name
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Store claims for downstream filters (TenantContextFilter)
            request.setAttribute("jwt.claims", claims);

        } catch (Exception e) {
            log.warn("Failed to parse JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
