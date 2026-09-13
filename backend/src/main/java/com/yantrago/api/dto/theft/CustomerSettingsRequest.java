package com.yantrago.api.dto.theft;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating customer theft protection defaults.
 *
 * Per AGENTS.md rule 12: validation via Bean Validation.
 */
public class CustomerSettingsRequest {

    @Min(value = 50, message = "geofence radius must be at least 50 meters")
    @Max(value = 1000, message = "geofence radius must not exceed 1000 meters")
    private Integer defaultGeofenceRadiusMeters = 200;

    @Min(value = 1, message = "speed threshold must be at least 1 km/h")
    @Max(value = 30, message = "speed threshold must not exceed 30 km/h")
    private Integer defaultSpeedThresholdKmh = 10;

    public Integer getDefaultGeofenceRadiusMeters() { return defaultGeofenceRadiusMeters; }
    public void setDefaultGeofenceRadiusMeters(Integer value) { this.defaultGeofenceRadiusMeters = value; }
    public Integer getDefaultSpeedThresholdKmh() { return defaultSpeedThresholdKmh; }
    public void setDefaultSpeedThresholdKmh(Integer value) { this.defaultSpeedThresholdKmh = value; }
}
