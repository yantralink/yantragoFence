package com.yantrago.api.dto.organization;

import jakarta.validation.constraints.Size;

public class UpdateOrganizationRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String slug;

    private String whiteLabelConfig;
    private Boolean isActive;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getWhiteLabelConfig() { return whiteLabelConfig; }
    public void setWhiteLabelConfig(String whiteLabelConfig) { this.whiteLabelConfig = whiteLabelConfig; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
