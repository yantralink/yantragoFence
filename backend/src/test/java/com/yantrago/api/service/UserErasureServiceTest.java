package com.yantrago.api.service;

import com.yantrago.api.model.User;
import com.yantrago.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PLAY-06 unit tests for UserErasureService.
 *
 * Verifies the erasure guarantee: sessions revoked first, every
 * user-scoped personal table purged, and the user row tombstoned so
 * audit/command records keep their user_id linkage without retaining PII.
 */
class UserErasureServiceTest {

    private UserRepository userRepository;
    private JdbcTemplate jdbcTemplate;
    private PasswordEncoder passwordEncoder;
    private SessionRevocationService sessionRevocationService;
    private UserErasureService erasureService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        sessionRevocationService = mock(SessionRevocationService.class);
        erasureService = new UserErasureService(userRepository, jdbcTemplate,
                passwordEncoder, sessionRevocationService);
    }

    private User activeUser() {
        User u = new User();
        u.setId(userId);
        u.setEmail("customer@example.com");
        u.setFullName("Real Name");
        u.setPhone("+919900112233");
        u.setPasswordHash("bcrypt-hash");
        u.setIsActive(true);
        u.setIsLocked(false);
        return u;
    }

    @Test
    @DisplayName("Sessions are revoked before any personal data is deleted")
    void erase_revokesSessionsFirst() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        erasureService.eraseUserPersonalData(userId);

        var order = inOrder(sessionRevocationService, jdbcTemplate);
        order.verify(sessionRevocationService).revokeAllSessions(userId);
        order.verify(jdbcTemplate).update(contains("notification_attempts"), eq(userId));
    }

    @Test
    @DisplayName("Every user-scoped personal table is purged")
    void erase_purgesAllPersonalTables() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        erasureService.eraseUserPersonalData(userId);

        String[] tables = {
                "notification_attempts", "notification_delivery", "notification_inbox",
                "notifications", "notification_preferences", "push_delivery_jobs",
                "user_device_tokens", "login_sessions", "refresh_tokens", "user_roles"
        };
        for (String table : tables) {
            verify(jdbcTemplate).update(contains("FROM " + table + " "), eq(userId));
        }
    }

    @Test
    @DisplayName("User row is tombstoned: PII stripped, id/org linkage retained")
    void erase_tombstonesUserRow() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        erasureService.eraseUserPersonalData(userId);

        assertThat(user.getEmail()).isEqualTo("deleted-" + userId + "@deleted.invalid");
        assertThat(user.getFullName()).isEqualTo("Deleted User");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getIsLocked()).isTrue();
        // Row is kept (audit/command linkage) — never hard-deleted.
        verify(userRepository).save(user);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Erasure is idempotent — a tombstoned user erases cleanly")
    void erase_idempotent() {
        User user = activeUser();
        user.setEmail("deleted-" + userId + "@deleted.invalid");
        user.setIsActive(false);
        user.setIsLocked(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        erasureService.eraseUserPersonalData(userId);

        verify(userRepository).save(user);
        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    @DisplayName("Unknown user id throws without touching any table")
    void erase_unknownUser_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> erasureService.eraseUserPersonalData(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(sessionRevocationService, never()).revokeAllSessions(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("eraseUserPersonalData requires an active transaction (MANDATORY)")
    void erase_requiresTransaction() throws NoSuchMethodException {
        Method m = UserErasureService.class
                .getMethod("eraseUserPersonalData", UUID.class);
        Transactional tx = m.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.MANDATORY);
    }
}
