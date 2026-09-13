package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Per-customer default settings for theft protection.
 *
 * Stores the customer's preferred geofence radius and speed threshold.
 * Used when the customer enables theft protection for a new machine.
 */
@Entity
@Table(name = "customer_settings",
        indexes = @jakarta.persistence.Index(name = "idx_customer_settings_customer", columnList = "customer_id"))
public class CustomerSettings extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "default_geofence_radius_meters", nullable = false)
    private Integer defaultGeofenceRadiusMeters = 200;

    @Column(name = "default_speed_threshold_kmh", nullable = false)
    private Integer defaultSpeedThresholdKmh = 10;

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public Integer getDefaultGeofenceRadiusMeters() { return defaultGeofenceRadiusMeters; }
    public void setDefaultGeofenceRadiusMeters(Integer value) { this.defaultGeofenceRadiusMeters = value; }
    public Integer getDefaultSpeedThresholdKmh() { return defaultSpeedThresholdKmh; }
    public void setDefaultSpeedThresholdKmh(Integer value) { this.defaultSpeedThresholdKmh = value; }
}
