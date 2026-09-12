package com.yantrago.api.dto.alert;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for alert rules.
 */
public record AlertRuleDto(
        UUID id,
        UUID organizationId,
        UUID machineId,
        String name,
        String alertType,
        String conditionConfig,
        String severity,
        Boolean isActive,
        Integer sustainMinutes,
        Integer recoveryMinutes,
        Integer escalationMinutes,
        String escalationSeverity,
        Integer ruleVersion,
        UUID updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
