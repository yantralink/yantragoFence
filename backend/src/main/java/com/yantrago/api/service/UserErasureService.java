package com.yantrago.api.service;

import com.yantrago.api.model.User;
import com.yantrago.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PLAY-06: user erasure (right-to-erasure) helper.
 *
 * Applied when a customer's account is deleted. Because machine command
 * history, audit logs, and other operational records reference the user
 * row, we do NOT delete the users row — we strip every piece of personal
 * data from it and physically delete all user-scoped personal tables.
 * The result:
 *  - the user_id still exists so audit/command records keep their linkage
 *    (AGENTS.md rule 6 — commands stay auditable),
 *  - no name, email, phone, credentials, sessions, push tokens,
 *    notification content, or login metadata survives,
 *  - tables that reference users without an ON DELETE rule
 *    (e.g. notification_dlq_replay_audit.replayed_by) cannot break.
 *
 * Deliberately kept: audit_logs, machine_commands, command_attempts,
 * alert history — pseudonymous operational records required for security
 * auditing and legal accounting, per the documented retention policy.
 */
@Service
public class UserErasureService {

    private static final Logger log = LoggerFactory.getLogger(UserErasureService.class);

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;

    public UserErasureService(UserRepository userRepository,
                              JdbcTemplate jdbcTemplate,
                              PasswordEncoder passwordEncoder,
                              SessionRevocationService sessionRevocationService) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevocationService = sessionRevocationService;
    }

    /**
     * Deletes all user-scoped personal data and tombstones the user row.
     * Idempotent — safe to call on an already-anonymized user.
     *
     * MANDATORY propagation: callers must run inside a transaction so the
     * tombstone write and the row deletes commit atomically — a partial
     * erasure must never persist.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void eraseUserPersonalData(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Invalidate live sessions first so the user is locked out even
        // while the rest of the erasure is in flight.
        sessionRevocationService.revokeAllSessions(userId);

        // User-scoped personal data — children before parents.
        // ON DELETE CASCADE does not fire because the user row stays.
        jdbcTemplate.update("DELETE FROM notification_attempts WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM notification_delivery WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM notification_inbox WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM notification_preferences WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM push_delivery_jobs WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_device_tokens WHERE user_id = ?", userId);
        // login_sessions holds ip_address / user_agent / device_fingerprint — PII.
        jdbcTemplate.update("DELETE FROM login_sessions WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        // Role grants are not PII but a tombstoned account must hold none.
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);

        // Tombstone the account — identity removed, linkage retained.
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setFullName("Deleted User");
        user.setPhone(null);
        user.setLastLoginAt(null);
        user.setPreferredLocale(null);
        // Unusable credential — random plaintext, hashed, never stored.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setIsActive(false);
        user.setIsLocked(true);
        userRepository.save(user);

        log.info("PLAY-06: erased personal data for user={}", userId);
    }
}
