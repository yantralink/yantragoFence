package com.yantrago.api.controller;

import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
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
import com.yantrago.api.service.AccountService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAY-06 tests for AccountController — account deletion requests.
 *
 * Verifies: authentication required (identity comes from the JWT only —
 * no user/org id is ever accepted from the client), the request is
 * delegated to AccountService, and 202 with a confirmation body.
 */
@WebMvcTest(controllers = AccountController.class)
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
class AccountControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AccountService accountService;

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

    private String accessToken() {
        return "Bearer " + jwtService.generateAccessToken(
                USER_ID, "user@example.com", ORG_ID, "CUSTOMER", "");
    }

    @Test
    void deletionRequest_returns401_withoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/account/deletion-request"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    @Test
    void deletionRequest_returns202_andRecordsRequest() throws Exception {
        mockMvc.perform(post("/api/v1/account/deletion-request")
                        .header("Authorization", accessToken()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("received"));

        verify(accountService).recordDeletionRequest(any());
    }

    @Test
    void deletionRequest_ignoresClientSuppliedIds() throws Exception {
        // A crafted body naming a different user must be ignored —
        // identity is taken from the JWT only.
        mockMvc.perform(post("/api/v1/account/deletion-request")
                        .header("Authorization", accessToken())
                        .contentType("application/json")
                        .content("{\"userId\":\"" + UUID.randomUUID()
                                + "\",\"organizationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isAccepted());

        verify(accountService).recordDeletionRequest(any());
    }
}
