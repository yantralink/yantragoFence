package com.yantrago.api.controller;

import com.yantrago.api.dto.auth.LoginRequest;
import com.yantrago.api.dto.auth.LoginResponse;
import com.yantrago.api.dto.auth.RefreshTokenRequest;
import com.yantrago.api.dto.auth.TokenResponse;
import com.yantrago.api.model.Organization;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.UserRepository;
import com.yantrago.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authentication endpoints: login, refresh, logout, me.
 * All endpoints are public (permitted in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public AuthController(AuthService authService, UserRepository userRepository,
                          OrganizationRepository organizationRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("viewer");
        String organizationName = null;
        if (user.getOrganizationId() != null) {
            organizationName = organizationRepository.findById(user.getOrganizationId())
                    .map(Organization::getName)
                    .orElse(null);
        }
        UserInfoResponse response = new UserInfoResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId() != null ? user.getOrganizationId().toString() : null,
                organizationName,
                role,
                user.getIsActive(),
                user.getPreferredLocale()
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/locale")
    public ResponseEntity<UserInfoResponse> updateLocale(
            @RequestBody UpdateLocaleRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String locale = request.locale();
        if (locale == null || (!locale.equals("en") && !locale.equals("hi") && !locale.equals("mr"))) {
            return ResponseEntity.badRequest().build();
        }
        user.setPreferredLocale(locale);
        userRepository.save(user);
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("viewer");
        String organizationName = null;
        if (user.getOrganizationId() != null) {
            organizationName = organizationRepository.findById(user.getOrganizationId())
                    .map(Organization::getName)
                    .orElse(null);
        }
        UserInfoResponse response = new UserInfoResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId() != null ? user.getOrganizationId().toString() : null,
                organizationName,
                role,
                user.getIsActive(),
                user.getPreferredLocale()
        );
        return ResponseEntity.ok(response);
    }

    public record UserInfoResponse(String id, String email, String fullName, String organizationId, String organizationName, String role, Boolean active, String preferredLocale) {}

    public record UpdateLocaleRequest(String locale) {}
}
