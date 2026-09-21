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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        return ResponseEntity.ok(toUserInfoResponse(user, authentication));
    }

    /**
     * Self-service update of the authenticated user's preferred notification
     * language. The target user comes from the JWT principal only — never
     * from the request body. Validation errors are mapped to the standard
     * error shape by GlobalExceptionHandler.
     */
    @PutMapping("/me/locale")
    public ResponseEntity<UserInfoResponse> updateLocale(
            @Valid @RequestBody UpdateLocaleRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        UUID userId = (UUID) authentication.getPrincipal();
        User user = authService.updatePreferredLocale(userId, request.locale());
        return ResponseEntity.ok(toUserInfoResponse(user, authentication));
    }

    private UserInfoResponse toUserInfoResponse(User user, Authentication authentication) {
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
        return new UserInfoResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId() != null ? user.getOrganizationId().toString() : null,
                organizationName,
                role,
                user.getIsActive(),
                user.getPreferredLocale(),
                user.getPhone()
        );
    }

    public record UserInfoResponse(String id, String email, String fullName, String organizationId, String organizationName, String role, Boolean active, String preferredLocale, String phoneNumber) {}

    /**
     * Canonical language codes only — region forms ("hi-IN"), blank and
     * unknown values are rejected by Bean Validation (standard 400 shape).
     */
    public record UpdateLocaleRequest(
            @NotBlank(message = "locale is required")
            @Pattern(regexp = "en|hi|mr", message = "locale must be one of: en, hi, mr")
            String locale) {}
}
