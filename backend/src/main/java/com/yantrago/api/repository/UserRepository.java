package com.yantrago.api.repository;

import com.yantrago.api.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Page<User> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndOrganizationId(String email, UUID organizationId);

    @Query("SELECT u FROM User u WHERE u.email = :email AND (u.organizationId = :organizationId OR u.organizationId IS NULL)")
    Optional<User> findByEmailForTenant(@Param("email") String email, @Param("organizationId") UUID organizationId);

    /**
     * All users EXCLUDING customer-role accounts (managed via /customers).
     * Super admin scope (no organization filter).
     */
    @Query(value = "SELECT u.* FROM users u WHERE u.id NOT IN ("
            + "SELECT ur.user_id FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE r.name = 'customer'"
            + ")",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.id NOT IN ("
                    + "SELECT ur.user_id FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE r.name = 'customer'"
                    + ")",
            nativeQuery = true)
    Page<User> findAllAdmins(Pageable pageable);

    /**
     * All users in an organization EXCLUDING customer-role accounts.
     */
    @Query(value = "SELECT u.* FROM users u WHERE u.organization_id = :organizationId AND u.id NOT IN ("
            + "SELECT ur.user_id FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE r.name = 'customer'"
            + ")",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.organization_id = :organizationId AND u.id NOT IN ("
                    + "SELECT ur.user_id FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE r.name = 'customer'"
                    + ")",
            nativeQuery = true)
    Page<User> findAdminsByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);
}
