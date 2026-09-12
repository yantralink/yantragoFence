package com.yantrago.api.service;

import com.yantrago.api.dto.user.CreateUserRequest;
import com.yantrago.api.dto.user.UpdateUserRequest;
import com.yantrago.api.dto.user.UserDto;
import com.yantrago.api.model.Role;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.RoleRepository;
import com.yantrago.api.repository.UserRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * User CRUD service.
 * Tenant isolation: all queries filter by organization_id from OwnerContextService.
 * Super_admin (null tenant context) can access users in any organization.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       OwnerContextService ownerContextService,
                       TenantGuard tenantGuard,
                       PasswordEncoder passwordEncoder,
                       JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> listUsers(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        Page<User> users = (orgId != null)
                ? userRepository.findByOrganizationId(orgId, pageable)
                : userRepository.findAll(pageable);
        return users.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        tenantGuard.validateTenantAccess(user.getOrganizationId());
        return toDto(user);
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();

        // Super_admin can specify which org to create the user in.
        // Tenant users can only create users in their own org.
        if (orgId == null && request.getOrganizationId() != null) {
            orgId = request.getOrganizationId();
        } else if (orgId == null) {
            throw new IllegalArgumentException("organizationId is required when creating users as super_admin");
        }

        if (userRepository.findByEmailAndOrganizationId(request.getEmail(), orgId).isPresent()) {
            throw new IllegalArgumentException("User with this email already exists in this organization");
        }

        User user = new User();
        user.setOrganizationId(orgId);
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setIsActive(true);
        user.setIsLocked(false);

        User savedUser = userRepository.saveAndFlush(user);

        // Assign role if specified
        String roleName = request.getRoleName() != null ? request.getRoleName() : "viewer";
        roleRepository.findByName(roleName).ifPresent(role -> {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    savedUser.getId(), role.getId());
        });

        // Phase 7: create default notification preferences (IN_APP + PUSH for all alert types)
        // Skip for super_admin users (null orgId) — they don't receive tenant-scoped notifications
        if (orgId != null) {
            createDefaultNotificationPreferences(savedUser.getId(), orgId);
        }

        log.info("Created user id={} email={} orgId={} role={}", savedUser.getId(), savedUser.getEmail(), orgId, roleName);
        return toDto(savedUser);
    }

    /**
     * Creates default notification preferences for a new user.
     * Defaults: IN_APP and PUSH channels enabled for all alert types.
     * Per Phase 7: ensures new users receive notifications without manual opt-in.
     */
    private void createDefaultNotificationPreferences(UUID userId, UUID orgId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled) " +
                            "VALUES (?, ?, 'IN_APP', NULL, true, true) ON CONFLICT DO NOTHING",
                    userId, orgId);
            jdbcTemplate.update(
                    "INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled) " +
                            "VALUES (?, ?, 'PUSH', NULL, true, true) ON CONFLICT DO NOTHING",
                    userId, orgId);
            log.info("Created default notification preferences for user={} orgId={}", userId, orgId);
        } catch (Exception e) {
            log.warn("Failed to create default notification preferences for user={}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public UserDto updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        tenantGuard.validateTenantAccess(user.getOrganizationId());

        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPassword() != null) user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getIsActive() != null) user.setIsActive(request.getIsActive());
        if (request.getIsLocked() != null) user.setIsLocked(request.getIsLocked());

        user = userRepository.save(user);
        log.info("Updated user id={}", user.getId());
        return toDto(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        tenantGuard.validateTenantAccess(user.getOrganizationId());
        userRepository.delete(user);
        log.info("Deleted user id={}", id);
    }

    @Transactional
    public UserDto activateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setIsActive(true);
        user = userRepository.save(user);
        log.info("Activated user id={}", id);
        return toDto(user);
    }

    @Transactional
    public UserDto deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setIsActive(false);
        user = userRepository.save(user);
        log.info("Deactivated user id={}", id);
        return toDto(user);
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getOrganizationId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getIsActive(),
                user.getIsLocked()
        );
    }
}
