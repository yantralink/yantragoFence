package com.yantrago.api.dto.organization;

import java.util.UUID;

public class OrganizationDto {

    private UUID id;
    private String name;
    private String slug;
    private String whiteLabelConfig;
    private Boolean isActive;

    public OrganizationDto() {}

    public OrganizationDto(UUID id, String name, String slug, String whiteLabelConfig, Boolean isActive) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.whiteLabelConfig = whiteLabelConfig;
        this.isActive = isActive;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getWhiteLabelConfig() { return whiteLabelConfig; }
    public void setWhiteLabelConfig(String whiteLabelConfig) { this.whiteLabelConfig = whiteLabelConfig; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
