package com.yantrago.api.service;

import com.yantrago.api.model.User;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.RefreshTokenRepository;
import com.yantrago.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService#updatePreferredLocale(UUID, String)}.
 *
 * Per Multilingual Plan Phase 4: idempotent self-service preference updates.
 * Locale-code validation is Bean Validation on the request DTO (controller
 * slice tests cover the 400 shape); the service only handles persistence.
 */
class AuthServiceUpdateLocaleTest {

    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JwtService jwtService = mock(JwtService.class);
        authService = new AuthService(userRepository, organizationRepository,
                refreshTokenRepository, jwtService, jdbcTemplate);
    }

    private User userWithLocale(String preferredLocale) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPreferredLocale(preferredLocale);
        return user;
    }

    @Test
    @DisplayName("valid update persists the new locale")
    void updatePersistsNewLocale() {
        UUID userId = UUID.randomUUID();
        User user = userWithLocale("en");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.updatePreferredLocale(userId, "mr");

        assertEquals("mr", result.getPreferredLocale());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("re-sending the current locale is idempotent — no write")
    void updateIsIdempotent() {
        UUID userId = UUID.randomUUID();
        User user = userWithLocale("hi");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User first = authService.updatePreferredLocale(userId, "hi");
        User second = authService.updatePreferredLocale(userId, "hi");

        assertSame(first, second);
        assertEquals("hi", second.getPreferredLocale());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("repeated updates apply the latest value (last write wins)")
    void repeatedUpdatesApplyLatestValue() {
        UUID userId = UUID.randomUUID();
        User user = userWithLocale(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updatePreferredLocale(userId, "hi");
        authService.updatePreferredLocale(userId, "mr");

        assertEquals("mr", user.getPreferredLocale());
        verify(userRepository, org.mockito.Mockito.times(2)).save(user);
    }

    @Test
    @DisplayName("unknown user is rejected (IllegalArgumentException → 400)")
    void unknownUserRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.updatePreferredLocale(userId, "en"));
    }

    @Test
    @DisplayName("unset (null) stored locale is accepted as previous value")
    void nullStoredLocaleHandled() {
        UUID userId = UUID.randomUUID();
        User user = userWithLocale(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.updatePreferredLocale(userId, "en");

        assertEquals("en", result.getPreferredLocale());
        verify(userRepository).save(user);
    }
}
