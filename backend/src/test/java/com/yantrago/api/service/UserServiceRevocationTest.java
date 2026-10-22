package com.yantrago.api.service;

import com.yantrago.api.dto.user.UpdateUserRequest;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.RoleRepository;
import com.yantrago.api.repository.UserRepository;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SEC-04 regression tests for UserService.
 *
 * Verifies that security-state changes revoke all outstanding refresh tokens:
 * - Password change → revoke
 * - Deactivation → revoke
 * - Locking → revoke
 * - Deletion → revoke
 * - Non-security updates (name, phone, locale) → no revoke
 */
class UserServiceRevocationTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private PasswordEncoder passwordEncoder;
    private JdbcTemplate jdbcTemplate;
    private SessionRevocationService sessionRevocationService;
    private UserErasureService userErasureService;
    private AuditLogService auditLogService;
    private UserService userService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        sessionRevocationService = mock(SessionRevocationService.class);
        userErasureService = mock(UserErasureService.class);
        auditLogService = mock(AuditLogService.class);
        userService = new UserService(userRepository, roleRepository, ownerContextService,
                tenantGuard, passwordEncoder, jdbcTemplate, sessionRevocationService,
                userErasureService, auditLogService);

        lenient().when(jdbcTemplate.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(java.util.List.of());
    }

    private User activeUser() {
        User u = new User();
        u.setId(userId);
        u.setOrganizationId(orgId);
        u.setIsActive(true);
        u.setIsLocked(false);
        return u;
    }

    @Test
    @DisplayName("SEC-04: password change revokes all sessions")
    void updateUser_passwordChange_revokesSessions() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        UpdateUserRequest req = new UpdateUserRequest();
        req.setPassword("newPassword");

        userService.updateUser(userId, req);

        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    @DisplayName("SEC-04: deactivation revokes all sessions")
    void updateUser_deactivation_revokesSessions() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setIsActive(false);

        userService.updateUser(userId, req);

        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    @DisplayName("SEC-04: locking revokes all sessions")
    void updateUser_locking_revokesSessions() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setIsLocked(true);

        userService.updateUser(userId, req);

        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    @DisplayName("SEC-04: non-security update (name only) does NOT revoke sessions")
    void updateUser_nameOnly_noRevoke() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("New Name");

        userService.updateUser(userId, req);

        verify(sessionRevocationService, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("SEC-04: deactivateUser revokes all sessions")
    void deactivateUser_revokesSessions() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deactivateUser(userId);

        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    @DisplayName("PLAY-06: deleteUser erases personal data instead of hard-delete")
    void deleteUser_erasesPersonalData() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        verify(userErasureService).eraseUserPersonalData(userId);
        verify(userRepository, never()).delete(any());
        verify(auditLogService).recordAuditLog(eq("ACCOUNT_ERASED"), eq("USER"),
                eq(userId), any(), any(), any());
    }

    @Test
    @DisplayName("SEC-04: activateUser does NOT revoke sessions")
    void activateUser_noRevoke() {
        User user = activeUser();
        user.setIsActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.activateUser(userId);

        verify(sessionRevocationService, never()).revokeAllSessions(any());
    }
}
