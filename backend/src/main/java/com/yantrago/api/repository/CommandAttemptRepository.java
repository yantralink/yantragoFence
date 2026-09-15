package com.yantrago.api.repository;

import com.yantrago.api.model.CommandAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommandAttemptRepository extends JpaRepository<CommandAttempt, UUID> {
}
