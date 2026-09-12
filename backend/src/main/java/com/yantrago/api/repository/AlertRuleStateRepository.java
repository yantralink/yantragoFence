package com.yantrago.api.repository;

import com.yantrago.api.model.AlertRuleState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertRuleStateRepository extends JpaRepository<AlertRuleState, UUID> {

    Optional<AlertRuleState> findByRuleIdAndMachineId(UUID ruleId, UUID machineId);
}
