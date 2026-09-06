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
}
