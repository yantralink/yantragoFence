package com.yantrago.api.controller;

import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.UserRepository;
import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.service.AuthService;
import com.yantrago.api.service.JwtService;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the self-service preferred-locale endpoints (Multilingual Plan
 * Phase 4):
 * - PUT /api/v1/auth/me/locale validates the canonical code set via Bean
 *   Validation and returns the standard error shape on failure
 * - the target user is resolved from the JWT principal only (no cross-user
 *   tampering is possible: the request body has no user/org field)
 * - GET /api/v1/auth/me exposes preferredLocale, including the unset (null)
 *   state
 * - unauthenticated requests are rejected with 401
 */
@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({SecurityConfig.class, WebMvcConfig.class, JwtAuthFilter.class, TenantContextFilter.class,
         RateLimitFilter.class, AuditLogInterceptor.class, JwtService.class,
         OwnerContextService.class, TenantGuard.class, PermissionEvaluator.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.access.secret=test-access-secret-at-least-32-characters-long",
        "jwt.refresh.secret=test-refresh-secret-at-least-32-characters-long",
        "jwt.access.ttl=3600",
        "jwt.refresh.ttl=2592000"
})
class AuthLocaleControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OrganizationRepository organizationRepository;

    private String accessToken() {
        return "Bearer " + jwtService.generateAccessToken(
                USER_ID, "user@example.com", ORG_ID, "CUSTOMER", "");
    }

    private User userWithLocale(String preferredLocale) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        user.setFullName("Test User");
        user.setOrganizationId(ORG_ID);
        user.setIsActive(true);
        user.setPreferredLocale(preferredLocale);
        return user;
    }

    @Test
    @DisplayName("PUT /me/locale with a valid code returns 200 and the new locale")
    void updateLocale_valid_returns200AndLocale() throws Exception {
        when(authService.updatePreferredLocale(eq(USER_ID), eq("mr")))
                .thenReturn(userWithLocale("mr"));

        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"mr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLocale").value("mr"));

        // Self-service: the service was called with the JWT principal's id
        verify(authService).updatePreferredLocale(eq(USER_ID), eq("mr"));
    }

    @Test
    @DisplayName("repeated identical updates succeed (idempotent)")
    void updateLocale_repeat_succeeds() throws Exception {
        when(authService.updatePreferredLocale(any(UUID.class), eq("hi")))
                .thenReturn(userWithLocale("hi"));

        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(put("/api/v1/auth/me/locale")
                            .header("Authorization", accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"locale\":\"hi\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preferredLocale").value("hi"))
                    .andReturn();
            assertEquals(200, result.getResponse().getStatus());
        }
    }

    @Test
    @DisplayName("blank locale is rejected with the standard error shape")
    void updateLocale_blank_returns400StandardShape() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("missing locale field is rejected")
    void updateLocale_missingField_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unknown code is rejected")
    void updateLocale_unknownCode_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"fr\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("region-form locale (hi-IN) is rejected")
    void updateLocale_regionForm_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"hi-IN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("oversized value is rejected")
    void updateLocale_oversized_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"enmrxxxxx\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unauthenticated request returns 401")
    void updateLocale_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"mr\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me returns the stored locale")
    void me_returnsStoredLocale() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(java.util.Optional.of(userWithLocale("hi")));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLocale").value("hi"));
    }

    @Test
    @DisplayName("GET /me omits preferredLocale when unset (old-DTO compatible)")
    void me_returnsNullLocale_whenUnset() throws Exception {
        // The app's ObjectMapper omits null fields, so an unset preference is
        // serialized as an ABSENT field — the same shape older servers send.
        // The mobile client treats a missing preferredLocale as 'en' fallback.
        when(userRepository.findById(USER_ID)).thenReturn(java.util.Optional.of(userWithLocale(null)));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLocale").doesNotExist());
    }

    @Test
    @DisplayName("cross-user tampering is impossible — body has no target field")
    void updateLocale_bodyCannotTargetAnotherUser() throws Exception {
        when(authService.updatePreferredLocale(any(UUID.class), eq("en")))
                .thenReturn(userWithLocale("en"));

        // Even if a client smuggles a userId field into the body, the
        // controller ignores it and uses the JWT principal.
        mockMvc.perform(put("/api/v1/auth/me/locale")
                        .header("Authorization", accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"en\",\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        verify(authService).updatePreferredLocale(eq(USER_ID), eq("en"));
    }
}
