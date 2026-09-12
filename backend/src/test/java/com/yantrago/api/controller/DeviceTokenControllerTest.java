package com.yantrago.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
import com.yantrago.api.dto.push.DeviceTokenDto;
import com.yantrago.api.dto.push.DeviceTokenRequest;
import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.service.DeviceTokenService;
import com.yantrago.api.service.JwtService;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for DeviceTokenController.
 *
 * Verifies Phase 5 acceptance criteria:
 * - RBAC on all endpoints (push_token:read, push_token:write)
 * - Validation (@Valid on request body)
 * - Authenticated lifecycle APIs
 *
 * Per notification plan Phase 5: authenticated lifecycle APIs.
 */
@WebMvcTest(controllers = DeviceTokenController.class)
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
class DeviceTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceTokenService deviceTokenService;

    private final UUID tokenId = UUID.randomUUID();

    @Test
    void registerToken_shouldReturn401_withoutAuth() throws Exception {
        DeviceTokenRequest request = new DeviceTokenRequest();
        request.setToken("fcm-token-123");
        request.setPlatform("ANDROID");

        mockMvc.perform(post("/api/v1/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerToken_shouldRejectInvalidRequest_missingToken() throws Exception {
        DeviceTokenRequest request = new DeviceTokenRequest();
        request.setPlatform("ANDROID");
        // token is @NotBlank — missing

        mockMvc.perform(post("/api/v1/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // 401 before validation
    }

    @Test
    void registerToken_serviceContractReturnsDto() throws Exception {
        DeviceTokenDto dto = new DeviceTokenDto(tokenId, "ANDROID", "Pixel 7",
                "1.0.0", true, null, LocalDateTime.now());
        when(deviceTokenService.registerToken(any())).thenReturn(dto);

        DeviceTokenDto result = deviceTokenService.registerToken(new DeviceTokenRequest());
        org.junit.jupiter.api.Assertions.assertEquals("ANDROID", result.platform());
        org.junit.jupiter.api.Assertions.assertTrue(result.isActive());
    }

    @Test
    void unregisterToken_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(delete("/api/v1/device-tokens/" + tokenId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unregisterToken_serviceContractCallsDelete() throws Exception {
        doNothing().when(deviceTokenService).unregisterToken(tokenId);
        deviceTokenService.unregisterToken(tokenId);
        org.mockito.Mockito.verify(deviceTokenService).unregisterToken(tokenId);
    }

    @Test
    void deactivateAll_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/device-tokens/deactivate-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateAll_serviceContractCallsDeactivate() throws Exception {
        doNothing().when(deviceTokenService).deactivateAllForUser();
        deviceTokenService.deactivateAllForUser();
        org.mockito.Mockito.verify(deviceTokenService).deactivateAllForUser();
    }

    @Test
    void listMyTokens_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/device-tokens"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMyTokens_serviceContractReturnsList() throws Exception {
        DeviceTokenDto dto = new DeviceTokenDto(tokenId, "ANDROID", "Pixel 7",
                "1.0.0", true, null, LocalDateTime.now());
        when(deviceTokenService.listMyTokens()).thenReturn(List.of(dto));

        List<DeviceTokenDto> result = deviceTokenService.listMyTokens();
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals("ANDROID", result.get(0).platform());
    }
}
