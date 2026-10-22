package com.yantrago.api.controller;

import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
import com.yantrago.api.dto.geocoding.AddressDto;
import com.yantrago.api.exception.GeocodingProviderException;
import com.yantrago.api.model.Organization;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.UserRepository;
import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.PostAuthRateLimitFilter;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.security.TrustedProxyResolver;
import com.yantrago.api.service.GeocodingService;
import com.yantrago.api.service.JwtService;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GeocodingController — SEC-15 backend geocoding proxy.
 *
 * Verifies: authentication required, location:read RBAC, lang validation
 * (400 not 500), 404 for location-less machines, 502 for provider
 * failures, and response shape.
 */
@WebMvcTest(controllers = GeocodingController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtAuthFilter.class, TenantContextFilter.class,
         RateLimitFilter.class, PostAuthRateLimitFilter.class, TrustedProxyResolver.class,
         AuditLogInterceptor.class, JwtService.class,
         OwnerContextService.class, TenantGuard.class, PermissionEvaluator.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.access.secret=test-access-secret-at-least-32-characters-long",
        "jwt.refresh.secret=test-refresh-secret-at-least-32-characters-long",
        "jwt.access.ttl=3600",
        "jwt.refresh.ttl=2592000"
})
class GeocodingControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MACHINE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private GeocodingService geocodingService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        user.setFullName("Test User");
        user.setOrganizationId(ORG_ID);
        user.setIsActive(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Organization org = new Organization();
        org.setId(ORG_ID);
        org.setIsActive(true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
    }

    private String accessToken(String permissions) {
        return "Bearer " + jwtService.generateAccessToken(
                USER_ID, "user@example.com", ORG_ID, "CUSTOMER", permissions);
    }

    @Test
    void reverseGeocode_returns401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding/reverse")
                        .param("machineId", MACHINE_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reverseGeocode_returns403_withoutLocationReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding/reverse")
                        .header("Authorization", accessToken(""))
                        .param("machineId", MACHINE_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reverseGeocode_returns200_withAddress() throws Exception {
        when(geocodingService.reverseGeocode(MACHINE_ID, "en"))
                .thenReturn(new AddressDto("Pune, India", "Farm Road",
                        "Kothrud", null, "Pune", null, "Maharashtra",
                        "411038", "India"));

        mockMvc.perform(get("/api/v1/geocoding/reverse")
                        .header("Authorization", accessToken("location:read"))
                        .param("machineId", MACHINE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Pune, India"))
                .andExpect(jsonPath("$.city").value("Pune"))
                .andExpect(jsonPath("$.country").value("India"));
    }

    @Test
    void reverseGeocode_returns404_whenMachineHasNoLocation() throws Exception {
        when(geocodingService.reverseGeocode(any(UUID.class), anyString()))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/geocoding/reverse")
                        .header("Authorization", accessToken("location:read"))
                        .param("machineId", MACHINE_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reverseGeocode_returns400_whenLangInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding/reverse")
                        .header("Authorization", accessToken("location:read"))
                        .param("machineId", MACHINE_ID.toString())
                        .param("lang", "!!!not-a-lang!!!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reverseGeocode_returns502_whenProviderFails() throws Exception {
        when(geocodingService.reverseGeocode(any(UUID.class), anyString()))
                .thenThrow(new GeocodingProviderException("provider down"));

        mockMvc.perform(get("/api/v1/geocoding/reverse")
                        .header("Authorization", accessToken("location:read"))
                        .param("machineId", MACHINE_ID.toString()))
                .andExpect(status().isBadGateway());
    }
}
