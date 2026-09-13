package com.yantrago.api.dto.theft;

import java.util.UUID;

/**
 * Status DTO for theft protection on a machine.
 *
 * Shows whether geofence and movement alert are active, along with
 * the geofence center/radius and the machine's latest GPS location.
 */
public record TheftProtectionStatusDto(
        UUID machineId,
        boolean protectionEnabled,
        boolean geofenceActive,
        boolean movementRuleActive,
        UUID geofenceId,
        Double geofenceLatitude,
        Double geofenceLongitude,
        Integer geofenceRadiusMeters,
        Double machineLatitude,
        Double machineLongitude
) {}
