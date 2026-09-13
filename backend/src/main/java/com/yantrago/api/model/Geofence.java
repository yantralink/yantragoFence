package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Geofence entity — circular boundary for theft detection.
 *
 * One active geofence per machine. The center column (PostGIS GEOGRAPHY)
 * is auto-populated by a DB trigger from latitude/longitude — not mapped in JPA.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 */
@Entity
@Table(name = "geofences", indexes = {
        @jakarta.persistence.Index(name = "idx_geofences_org_machine", columnList = "organization_id, machine_id")
})
public class Geofence extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters = 100;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // center (GEOGRAPHY POINT) is auto-populated by DB trigger — not mapped in JPA.

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Integer getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(Integer radiusMeters) { this.radiusMeters = radiusMeters; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
